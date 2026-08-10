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
import gov.nasa.worldwind.util.Level;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.LevelSet;

import java.util.Iterator;

/**
 * Bulk imagery downloader limited by cache folder names (for example Earth/Bing/0 .. Earth/Bing/14).
 * <p>
 * The min/max values are the folder names under the layer cache root, not the internal WorldWind level index.
 * For Bing, empty leading levels are skipped so folder {@code 0} is the first non-empty zoom.
 * </p>
 *
 * @author Cursor Agent
 */
public class LevelRangeTiledImageBulkDownloader extends BasicTiledImageLayerBulkDownloader
{
    protected final int minFolder;
    protected final int maxFolder;

    public LevelRangeTiledImageBulkDownloader(BasicTiledImageLayer layer, Sector sector, int minFolder, int maxFolder,
        FileStore fileStore, BulkRetrievalListener listener)
    {
        super(layer, sector, texelSizeForFolder(layer, maxFolder),
            fileStore != null ? fileStore : WorldWind.getDataFileStore(), listener);
        this.minFolder = Math.max(0, minFolder);
        this.maxFolder = Math.max(this.minFolder, maxFolder);
    }

    public long estimateMissingDataSizeBytes()
    {
        return this.getEstimatedMissingDataSize();
    }

    @Override
    public void run()
    {
        try
        {
            this.progress.setTotalCount(this.estimateMissingTilesCount(20));
            this.progress.setTotalSize(this.progress.getTotalCount() * estimateAverageTileSize());

            for (int levelNumber = 0; levelNumber <= this.level; levelNumber++)
            {
                if (!this.shouldDownloadLevel(levelNumber))
                {
                    continue;
                }

                int div = this.computeRegionDivisions(this.sector, levelNumber, MAX_TILE_COUNT_PER_REGION);
                Iterator<Sector> regionsIterator = this.getRegionIterator(this.sector, div);

                while (regionsIterator.hasNext())
                {
                    Sector region = regionsIterator.next();
                    this.missingTiles = getMissingTilesInSector(region, levelNumber);

                    while (this.missingTiles.size() > 0)
                    {
                        submitMissingTilesRequests();
                        if (this.missingTiles.size() > 0)
                        {
                            Thread.sleep(RETRIEVAL_SERVICE_POLL_DELAY);
                        }
                    }
                }
            }

            this.progress.setTotalCount(this.progress.getCurrentCount());
            this.progress.setTotalSize(this.progress.getCurrentSize());
        }
        catch (InterruptedException e)
        {
            String message = Logging.getMessage("generic.BulkRetrievalInterrupted", this.layer.getName());
            Logging.logger().log(java.util.logging.Level.WARNING, message, e);
        }
        catch (Exception e)
        {
            String message = Logging.getMessage("generic.ExceptionDuringBulkRetrieval", this.layer.getName());
            Logging.logger().severe(message);
            throw new RuntimeException(message);
        }
    }

    @Override
    protected long estimateMissingTilesCount(int numSamples)
    {
        long totCount = 0;
        for (int levelNumber = 0; levelNumber <= this.level; levelNumber++)
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

        int sampleLevel = this.level;
        while (sampleLevel > 0 && !this.shouldDownloadLevel(sampleLevel))
        {
            sampleLevel--;
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
                regionMissing = getMissingTilesInSector(this.sector, sampleLevel).size();
            }
            else
            {
                for (Sector region : regions)
                {
                    regionCount += this.layer.countImagesInSector(region, sampleLevel);
                    regionMissing += getMissingTilesInSector(region, sampleLevel).size();
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

    protected boolean shouldDownloadLevel(int levelNumber)
    {
        LevelSet levels = this.layer.getLevels();
        if (levels.isLevelEmpty(levelNumber))
        {
            return false;
        }

        int folder = folderNameOf(levels.getLevel(levelNumber));
        return folder >= this.minFolder && folder <= this.maxFolder;
    }

    /**
     * Cache folder name under the layer root (Earth/Bing/{folder}/...).
     */
    protected static int folderNameOf(Level level)
    {
        if (level == null || level.isEmpty())
        {
            return -1;
        }

        String name = level.getLevelName();
        if (name != null && name.length() > 0)
        {
            try
            {
                return Integer.parseInt(name.trim());
            }
            catch (NumberFormatException ignore)
            {
                // Fall through to level number.
            }
        }

        return level.getLevelNumber();
    }

    protected static double texelSizeForFolder(BasicTiledImageLayer layer, int maxFolder)
    {
        int levelNumber = findWwLevelForMaxFolder(layer, maxFolder);
        return layer.getLevels().getLevel(levelNumber).getTexelSize();
    }

    protected static int findWwLevelForMaxFolder(BasicTiledImageLayer layer, int maxFolder)
    {
        LevelSet levels = layer.getLevels();
        int last = levels.getLastLevel().getLevelNumber();
        int best = 0;
        for (int i = 0; i <= last; i++)
        {
            if (levels.isLevelEmpty(i))
            {
                continue;
            }
            int folder = folderNameOf(levels.getLevel(i));
            if (folder >= 0 && folder <= maxFolder)
            {
                best = i;
            }
        }
        return best;
    }
}
