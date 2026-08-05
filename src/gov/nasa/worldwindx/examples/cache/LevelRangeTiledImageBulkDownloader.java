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
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.LevelSet;

import java.util.Iterator;

/**
 * Bulk imagery downloader limited to an inclusive WorldWind level range (for example 5-14).
 *
 * @author Cursor Agent
 */
public class LevelRangeTiledImageBulkDownloader extends BasicTiledImageLayerBulkDownloader
{
    protected final int minLevel;

    public LevelRangeTiledImageBulkDownloader(BasicTiledImageLayer layer, Sector sector, int minLevel, int maxLevel,
        FileStore fileStore, BulkRetrievalListener listener)
    {
        super(layer, sector, texelSizeForLevel(layer, maxLevel),
            fileStore != null ? fileStore : WorldWind.getDataFileStore(), listener);
        this.minLevel = clampMinLevel(layer, minLevel, this.level);
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

            for (int levelNumber = this.minLevel; levelNumber <= this.level; levelNumber++)
            {
                if (this.layer.getLevels().isLevelEmpty(levelNumber))
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
        for (int levelNumber = this.minLevel; levelNumber <= this.level; levelNumber++)
        {
            if (!this.layer.getLevels().isLevelEmpty(levelNumber))
            {
                totCount += this.layer.countImagesInSector(this.sector, levelNumber);
            }
        }

        if (totCount <= 0)
        {
            return 0;
        }

        int div = this.computeRegionDivisions(this.sector, this.level, 36);
        Sector[] regions = computeRandomRegions(this.sector, div, numSamples);
        long regionMissing = 0;
        long regionCount = 0;
        try
        {
            if (regions.length < numSamples)
            {
                regionCount = this.layer.countImagesInSector(this.sector, this.level);
                regionMissing = getMissingTilesInSector(this.sector, this.level).size();
            }
            else
            {
                for (Sector region : regions)
                {
                    regionCount += this.layer.countImagesInSector(region, this.level);
                    regionMissing += getMissingTilesInSector(region, this.level).size();
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

    protected static double texelSizeForLevel(BasicTiledImageLayer layer, int requestedMaxLevel)
    {
        int levelNumber = clampMaxLevel(layer, requestedMaxLevel);
        return layer.getLevels().getLevel(levelNumber).getTexelSize();
    }

    protected static int clampMaxLevel(BasicTiledImageLayer layer, int requestedMaxLevel)
    {
        LevelSet levels = layer.getLevels();
        int last = levels.getLastLevel().getLevelNumber();
        int max = Math.max(0, Math.min(requestedMaxLevel, last));
        while (max > 0 && levels.isLevelEmpty(max))
        {
            max--;
        }
        return max;
    }

    protected static int clampMinLevel(BasicTiledImageLayer layer, int requestedMinLevel, int effectiveMaxLevel)
    {
        LevelSet levels = layer.getLevels();
        int last = levels.getLastLevel().getLevelNumber();
        if (last < requestedMinLevel)
        {
            // Layer is coarser than the requested min zoom; download whatever it has.
            return 0;
        }

        int min = Math.max(0, Math.min(requestedMinLevel, effectiveMaxLevel));
        while (min < effectiveMaxLevel && levels.isLevelEmpty(min))
        {
            min++;
        }
        return min;
    }
}
