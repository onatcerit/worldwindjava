/*
 * Copyright 2006-2009, 2017, 2020 United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The NASA World Wind Java (WWJ) platform is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * NASA World Wind Java (WWJ) also contains the following 3rd party Open Source
 * software:
 *
 *     Jackson Parser – Licensed under Apache 2.0
 *     GDAL – Licensed under MIT
 *     JOGL – Licensed under  Berkeley Software Distribution (BSD)
 *     Gluegen – Licensed under Berkeley Software Distribution (BSD)
 *
 * A complete listing of 3rd Party software notices and licenses included in
 * NASA World Wind Java (WWJ)  can be found in the WorldWindJava-v2.2 3rd-party
 * notices and licenses PDF found in code directory.
 */
package gov.nasa.worldwindx.examples.cache;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.cache.FileStore;
import gov.nasa.worldwind.event.BulkRetrievalListener;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.BasicTiledImageLayer;
import gov.nasa.worldwind.layers.BasicTiledImageLayerBulkDownloader;
import gov.nasa.worldwind.layers.TextureTile;
import gov.nasa.worldwind.util.LevelSet;
import gov.nasa.worldwind.util.Logging;

import java.util.Iterator;

/**
 * Bulk imagery downloader limited to a range of cache folder names, for example <code>Earth/Bing/5</code> ..
 * <code>Earth/Bing/14</code>.
 * <p>
 * The min/max values are the folder names WorldWind writes under the layer's cache directory (see {@link CacheLevels}),
 * not the internal level index. Folders the layer cannot supply are simply not downloaded.
 * </p>
 * <p>
 * Progress is reported as <em>tiles resolved out of tiles in the sector</em>. Every tile the downloader touches counts:
 * tiles already in the cache, tiles the server reports as absent, and tiles newly downloaded. That makes the progress
 * bar grow steadily from 0% to 100% instead of being driven by a sampled estimate of the missing tiles, which is what
 * the stock downloader does and which leaves the bar pinned at 0% until the thread ends.
 * </p>
 *
 * @author Cursor Agent
 */
public class LevelRangeTiledImageBulkDownloader extends BasicTiledImageLayerBulkDownloader
{
    /**
     * Number of consecutive idle poll cycles tolerated before a region is abandoned. Without this guard a region whose
     * tiles never resolve keeps the thread spinning forever and the progress bar never completes.
     */
    protected static final int MAX_IDLE_POLL_CYCLES = 120;

    protected final int minFolder;
    protected final int maxFolder;
    protected final int downloadLevel;
    protected long averageTileSize = -1;

    public LevelRangeTiledImageBulkDownloader(BasicTiledImageLayer layer, Sector sector, int minFolder, int maxFolder,
        FileStore fileStore, BulkRetrievalListener listener)
    {
        super(layer, sector, texelSizeForFolder(layer, maxFolder),
            fileStore != null ? fileStore : WorldWind.getDataFileStore(), listener);
        this.minFolder = Math.max(0, minFolder);
        this.maxFolder = Math.max(this.minFolder, maxFolder);
        this.downloadLevel = CacheLevels.highestLevelNumberAtOrBelow(layer.getLevels(), this.maxFolder);
    }

    /**
     * Returns the folder range this downloader will actually write, which is the requested range clipped to the folders
     * the layer can supply.
     *
     * @return a two element array holding the effective minimum and maximum folder names, or null when nothing in the
     *         requested range is available.
     */
    public int[] getEffectiveFolderRange()
    {
        return CacheLevels.effectiveFolderRange(this.layer.getLevels(), this.minFolder, this.maxFolder);
    }

    /**
     * Returns the cache path the downloaded tiles are written under, for example <code>Earth/Bing</code>.
     *
     * @return the layer's cache directory name relative to the file store's write location.
     */
    public String getCachePath()
    {
        LevelSet levels = this.layer.getLevels();
        return levels.getFirstLevel() != null ? levels.getFirstLevel().getCacheName() : this.layer.getName();
    }

    public long estimateMissingDataSizeBytes()
    {
        return this.getEstimatedMissingDataSize();
    }

    /**
     * Returns the total number of tiles the sector covers across the selected folder range. This is exact arithmetic,
     * not a sample, so it is available immediately and gives the progress bar a stable denominator.
     *
     * @return the number of tiles this downloader will process.
     */
    public long countTilesToProcess()
    {
        long total = 0;

        for (int levelNumber = 0; levelNumber <= this.downloadLevel; levelNumber++)
        {
            if (!this.shouldDownloadLevel(levelNumber))
            {
                continue;
            }

            int div = this.computeRegionDivisions(this.sector, levelNumber, MAX_TILE_COUNT_PER_REGION);
            Iterator<Sector> regionsIterator = this.getRegionIterator(this.sector, div);
            while (regionsIterator.hasNext())
            {
                total += this.layer.countImagesInSector(regionsIterator.next(), levelNumber);
            }
        }

        return total;
    }

    @Override
    public void run()
    {
        try
        {
            this.averageTileSize = this.estimateAverageTileSize();

            long total = this.countTilesToProcess();
            this.progress.setTotalCount(total);
            this.progress.setTotalSize(total * this.averageTileSize);
            this.progress.setCurrentCount(0);
            this.progress.setCurrentSize(0);
            this.progress.setLastUpdateTime(System.currentTimeMillis());

            for (int levelNumber = 0; levelNumber <= this.downloadLevel; levelNumber++)
            {
                if (!this.shouldDownloadLevel(levelNumber))
                {
                    continue;
                }

                int div = this.computeRegionDivisions(this.sector, levelNumber, MAX_TILE_COUNT_PER_REGION);
                Iterator<Sector> regionsIterator = this.getRegionIterator(this.sector, div);

                while (regionsIterator.hasNext())
                {
                    this.downloadRegion(regionsIterator.next(), levelNumber);
                }
            }

            // Every tile in the sector has been accounted for, so finish at exactly 100%.
            this.progress.setTotalCount(Math.max(this.progress.getTotalCount(), this.progress.getCurrentCount()));
            this.progress.setCurrentCount(this.progress.getTotalCount());
            this.progress.setCurrentSize(this.progress.getTotalSize());
            this.progress.setLastUpdateTime(System.currentTimeMillis());
        }
        catch (InterruptedException e)
        {
            String message = Logging.getMessage("generic.BulkRetrievalInterrupted", this.layer.getName());
            Logging.logger().log(java.util.logging.Level.WARNING, message, e);
            Thread.currentThread().interrupt();
        }
        catch (Exception e)
        {
            String message = Logging.getMessage("generic.ExceptionDuringBulkRetrieval", this.layer.getName());
            Logging.logger().log(java.util.logging.Level.SEVERE, message, e);
        }
    }

    protected void downloadRegion(Sector region, int levelNumber) throws InterruptedException
    {
        long regionTileCount = this.layer.countImagesInSector(region, levelNumber);
        this.missingTiles = this.getMissingTilesInSector(region, levelNumber);

        // Tiles already cached, or already known to be absent, are done the moment we look at them.
        this.advanceProgress(regionTileCount - this.missingTiles.size());

        int idleCycles = 0;
        int lastRemaining = this.missingTiles.size();
        while (this.missingTiles.size() > 0)
        {
            this.submitMissingTilesRequests();

            int remaining = this.missingTiles.size();
            if (remaining > 0)
            {
                idleCycles = remaining < lastRemaining ? 0 : idleCycles + 1;
                lastRemaining = remaining;

                if (idleCycles >= MAX_IDLE_POLL_CYCLES)
                {
                    // The remaining tiles are not resolving; count them off and move on rather than hanging.
                    this.advanceProgress(remaining);
                    this.missingTiles.clear();
                    return;
                }

                Thread.sleep(RETRIEVAL_SERVICE_POLL_DELAY);
            }
        }
    }

    /**
     * Adds resolved tiles to the progress, keeping it monotonic. The size figures follow the count so that the
     * displayed megabytes and the displayed percentage never disagree.
     *
     * @param tiles number of tiles that just became resolved. Values less than one are ignored.
     */
    protected synchronized void advanceProgress(long tiles)
    {
        if (tiles < 1)
        {
            return;
        }

        long current = this.progress.getCurrentCount() + tiles;
        if (current > this.progress.getTotalCount())
        {
            this.progress.setTotalCount(current);
            this.progress.setTotalSize(current * this.tileSizeEstimate());
        }

        this.progress.setCurrentCount(current);
        this.progress.setCurrentSize(current * this.tileSizeEstimate());
        this.progress.setLastUpdateTime(System.currentTimeMillis());
    }

    protected long tileSizeEstimate()
    {
        if (this.averageTileSize < 0)
        {
            this.averageTileSize = this.estimateAverageTileSize();
        }
        return this.averageTileSize;
    }

    @Override
    protected synchronized void removeRetrievedTile(TextureTile tile)
    {
        if (this.missingTiles != null)
        {
            this.missingTiles.remove(tile);
        }
        this.advanceProgress(1);
    }

    @Override
    protected synchronized void removeAbsentTile(TextureTile tile)
    {
        if (this.missingTiles != null)
        {
            this.missingTiles.remove(tile);
        }
        // Unlike the stock downloader an absent tile does not shrink the total; it is simply one more tile resolved.
        this.advanceProgress(1);
    }

    @Override
    protected void normalizeProgress()
    {
        // Progress is kept consistent by advanceProgress; the stock rescaling would undo it.
    }

    @Override
    protected long estimateMissingTilesCount(int numSamples)
    {
        long totCount = 0;
        for (int levelNumber = 0; levelNumber <= this.downloadLevel; levelNumber++)
        {
            if (this.shouldDownloadLevel(levelNumber))
            {
                totCount += this.layer.countImagesInSector(this.sector, levelNumber);
            }
        }

        if (totCount <= 0)
        {
            return 0;
        }

        int sampleLevel = this.finestLevelInRange();
        if (sampleLevel < 0)
        {
            return 0;
        }

        int div = this.computeRegionDivisions(this.sector, sampleLevel, 36);
        Sector[] regions = computeRandomRegions(this.sector, div, numSamples);
        long regionMissing = 0;
        long regionCount = 0;
        try
        {
            if (regions.length < numSamples)
            {
                regionCount = this.layer.countImagesInSector(this.sector, sampleLevel);
                regionMissing = this.getMissingTilesInSector(this.sector, sampleLevel).size();
            }
            else
            {
                for (Sector region : regions)
                {
                    regionCount += this.layer.countImagesInSector(region, sampleLevel);
                    regionMissing += this.getMissingTilesInSector(region, sampleLevel).size();
                }
            }
        }
        catch (Exception e)
        {
            return totCount;
        }

        if (regionCount <= 0)
        {
            return totCount;
        }

        return (long) (totCount * ((double) regionMissing / regionCount));
    }

    protected int finestLevelInRange()
    {
        for (int levelNumber = this.downloadLevel; levelNumber >= 0; levelNumber--)
        {
            if (this.shouldDownloadLevel(levelNumber))
            {
                return levelNumber;
            }
        }
        return -1;
    }

    protected boolean shouldDownloadLevel(int levelNumber)
    {
        return CacheLevels.isFolderInRange(this.layer.getLevels(), levelNumber, this.minFolder, this.maxFolder);
    }

    /**
     * Returns the texel size of the finest level whose cache folder is at or below <code>maxFolder</code>, which is the
     * resolution the base class needs in order to stop at that level.
     *
     * @param layer     the layer to inspect.
     * @param maxFolder highest cache folder name to download.
     *
     * @return the target resolution in radians of latitude per texel.
     */
    protected static double texelSizeForFolder(BasicTiledImageLayer layer, int maxFolder)
    {
        LevelSet levels = layer.getLevels();
        int levelNumber = CacheLevels.highestLevelNumberAtOrBelow(levels, maxFolder);
        if (levelNumber < 0)
        {
            // Nothing in range; ask for the coarsest level so the download resolves to an empty set of levels.
            levelNumber = levels.getFirstLevel().getLevelNumber();
        }

        return levels.getLevel(levelNumber).getTexelSize();
    }
}
