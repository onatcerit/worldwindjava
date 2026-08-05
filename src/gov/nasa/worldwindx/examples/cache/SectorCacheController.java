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
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.cache.FileStore;
import gov.nasa.worldwind.event.BulkRetrievalListener;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.globes.ElevationModel;
import gov.nasa.worldwind.layers.BasicTiledImageLayer;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.ShapeAttributes;
import gov.nasa.worldwind.render.SurfaceSector;
import gov.nasa.worldwind.retrieve.BulkRetrievable;
import gov.nasa.worldwind.retrieve.BulkRetrievalThread;
import gov.nasa.worldwind.terrain.BasicElevationModel;
import gov.nasa.worldwind.terrain.CompoundElevationModel;
import gov.nasa.worldwindx.examples.ApplicationTemplate;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reusable controller that downloads imagery and elevation for a geographic sector into the WorldWind cache.
 * <p>
 * Downloads are limited to a WorldWind level range (default 5-14) so cache size stays manageable.
 * </p>
 *
 * @author Cursor Agent
 */
public class SectorCacheController
{
    public static final int DEFAULT_MIN_LEVEL = 5;
    public static final int DEFAULT_MAX_LEVEL = 14;

    protected final WorldWindow wwd;
    protected final RenderableLayer previewLayer;
    protected final SurfaceSector previewSector;
    protected FileStore cache;
    protected int minLevel = DEFAULT_MIN_LEVEL;
    protected int maxLevel = DEFAULT_MAX_LEVEL;

    public SectorCacheController(WorldWindow wwd)
    {
        if (wwd == null)
        {
            throw new IllegalArgumentException("WorldWindow is required");
        }

        this.wwd = wwd;
        this.previewLayer = new RenderableLayer();
        this.previewLayer.setName("Cache Sector Preview");
        this.previewLayer.setPickEnabled(false);

        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setInteriorMaterial(Material.WHITE);
        attrs.setInteriorOpacity(0.15);
        attrs.setOutlineMaterial(new Material(new Color(200, 40, 40)));
        attrs.setOutlineOpacity(0.8);
        attrs.setOutlineWidth(2);
        attrs.setDrawInterior(true);
        attrs.setDrawOutline(true);

        this.previewSector = new SurfaceSector(Sector.EMPTY_SECTOR);
        this.previewSector.setAttributes(attrs);
        this.previewSector.setVisible(false);
        this.previewLayer.addRenderable(this.previewSector);

        ApplicationTemplate.insertBeforeCompass(this.wwd, this.previewLayer);
    }

    public WorldWindow getWwd()
    {
        return this.wwd;
    }

    public FileStore getCache()
    {
        return this.cache;
    }

    public void setCache(FileStore cache)
    {
        this.cache = cache;
    }

    public int getMinLevel()
    {
        return this.minLevel;
    }

    public int getMaxLevel()
    {
        return this.maxLevel;
    }

    /**
     * Sets the inclusive WorldWind level range used for bulk downloads.
     *
     * @param minLevel minimum level (inclusive)
     * @param maxLevel maximum level (inclusive)
     */
    public void setLevelRange(int minLevel, int maxLevel)
    {
        if (minLevel < 0 || maxLevel < 0 || minLevel > maxLevel)
        {
            throw new IllegalArgumentException("Invalid level range: " + minLevel + "-" + maxLevel);
        }
        this.minLevel = minLevel;
        this.maxLevel = maxLevel;
    }

    public List<BulkRetrievable> listBulkRetrievables()
    {
        ArrayList<BulkRetrievable> list = new ArrayList<BulkRetrievable>();

        for (Layer layer : this.wwd.getModel().getLayers())
        {
            if (layer instanceof BulkRetrievable)
            {
                list.add((BulkRetrievable) layer);
            }
        }

        ElevationModel elevationModel = this.wwd.getModel().getGlobe().getElevationModel();
        if (elevationModel instanceof CompoundElevationModel)
        {
            for (ElevationModel model : ((CompoundElevationModel) elevationModel).getElevationModels())
            {
                if (model instanceof BulkRetrievable)
                {
                    list.add((BulkRetrievable) model);
                }
            }
        }
        else if (elevationModel instanceof BulkRetrievable)
        {
            list.add((BulkRetrievable) elevationModel);
        }

        return Collections.unmodifiableList(list);
    }

    public void showSectorPreview(Sector sector)
    {
        if (sector == null || sector.equals(Sector.EMPTY_SECTOR))
        {
            this.previewSector.setVisible(false);
            this.previewSector.setSector(Sector.EMPTY_SECTOR);
        }
        else
        {
            this.previewSector.setSector(sector);
            this.previewSector.setVisible(true);
        }
        this.wwd.redraw();
    }

    public void clearSectorPreview()
    {
        this.showSectorPreview(null);
    }

    public long estimateMissingDataSize(BulkRetrievable retrievable, Sector sector)
    {
        if (retrievable == null || sector == null)
        {
            return -1;
        }

        try
        {
            FileStore fileStore = this.cache != null ? this.cache : WorldWind.getDataFileStore();
            if (retrievable instanceof BasicTiledImageLayer)
            {
                LevelRangeTiledImageBulkDownloader downloader = new LevelRangeTiledImageBulkDownloader(
                    (BasicTiledImageLayer) retrievable, sector, this.minLevel, this.maxLevel, fileStore, null);
                return downloader.estimateMissingDataSizeBytes();
            }
            if (retrievable instanceof BasicElevationModel)
            {
                LevelRangeElevationModelBulkDownloader downloader = new LevelRangeElevationModelBulkDownloader(
                    (BasicElevationModel) retrievable, sector, this.minLevel, this.maxLevel, fileStore, null);
                return downloader.estimateMissingDataSizeBytes();
            }

            // Fallback: use finest available level within maxLevel when possible.
            return retrievable.getEstimatedMissingDataSize(sector, 0, this.cache);
        }
        catch (Exception e)
        {
            return -1;
        }
    }

    public List<BulkRetrievalThread> startDownloads(Sector sector, Iterable<? extends BulkRetrievable> retrievables,
        BulkRetrievalListener listener)
    {
        ArrayList<BulkRetrievalThread> threads = new ArrayList<BulkRetrievalThread>();
        if (sector == null || retrievables == null)
        {
            return threads;
        }

        FileStore fileStore = this.cache != null ? this.cache : WorldWind.getDataFileStore();

        for (BulkRetrievable retrievable : retrievables)
        {
            if (retrievable == null)
            {
                continue;
            }

            BulkRetrievalThread thread = null;
            if (retrievable instanceof BasicTiledImageLayer)
            {
                BasicTiledImageLayer layer = (BasicTiledImageLayer) retrievable;
                Sector target = layer.getLevels().getSector().intersection(sector);
                if (target != null)
                {
                    thread = new LevelRangeTiledImageBulkDownloader(layer, target, this.minLevel, this.maxLevel,
                        fileStore, listener);
                }
            }
            else if (retrievable instanceof BasicElevationModel)
            {
                BasicElevationModel model = (BasicElevationModel) retrievable;
                Sector target = model.getLevels().getSector().intersection(sector);
                if (target != null)
                {
                    thread = new LevelRangeElevationModelBulkDownloader(model, target, this.minLevel, this.maxLevel,
                        fileStore, listener);
                }
            }
            else
            {
                thread = retrievable.makeLocal(sector, 0, this.cache, listener);
            }

            if (thread != null)
            {
                thread.setDaemon(true);
                thread.start();
                threads.add(thread);
            }
        }

        return threads;
    }

    public static Sector sectorFromDegrees(double minLatitude, double maxLatitude, double minLongitude,
        double maxLongitude)
    {
        if (minLatitude < -90 || minLatitude > 90 || maxLatitude < -90 || maxLatitude > 90)
        {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees.");
        }
        if (minLongitude < -180 || minLongitude > 180 || maxLongitude < -180 || maxLongitude > 180)
        {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees.");
        }
        if (minLatitude >= maxLatitude)
        {
            throw new IllegalArgumentException("Minimum latitude must be less than maximum latitude.");
        }
        if (minLongitude >= maxLongitude)
        {
            throw new IllegalArgumentException("Minimum longitude must be less than maximum longitude.");
        }

        return Sector.fromDegrees(minLatitude, maxLatitude, minLongitude, maxLongitude);
    }

    public static String makeSectorDescription(Sector sector)
    {
        if (sector == null)
        {
            return "-";
        }

        return String.format("S %7.4f\u00B0 W %7.4f\u00B0 N %7.4f\u00B0 E %7.4f\u00B0",
            sector.getMinLatitude().degrees,
            sector.getMinLongitude().degrees,
            sector.getMaxLatitude().degrees,
            sector.getMaxLongitude().degrees);
    }

    public static String makeSizeDescription(long size)
    {
        if (size < 0)
        {
            return "-";
        }

        double sizeInMegaBytes = size / 1024d / 1024d;
        if (sizeInMegaBytes < 1024)
        {
            return String.format("%,.1f MB", sizeInMegaBytes);
        }
        else if (sizeInMegaBytes < 1024 * 1024)
        {
            return String.format("%,.1f GB", sizeInMegaBytes / 1024);
        }
        return String.format("%,.1f TB", sizeInMegaBytes / 1024 / 1024);
    }
}
