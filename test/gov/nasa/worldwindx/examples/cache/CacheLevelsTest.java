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

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.util.LevelSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Checks the mapping between WorldWind level numbers and the zoom folder names written to the file cache, using a level
 * set configured exactly like config/Earth/BingImagery.xml.
 */
@RunWith(JUnit4.class)
public class CacheLevelsTest
{
    /**
     * Builds a level set matching the Bing imagery configuration: 16 levels of which the first 6 hold no data, so the
     * cache folders are Earth/Bing/0 through Earth/Bing/9.
     *
     * @return the level set under test.
     */
    private static LevelSet createBingLikeLevelSet()
    {
        AVListImpl params = new AVListImpl();
        params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA, LatLon.fromDegrees(36, 36));
        params.setValue(AVKey.SECTOR, Sector.FULL_SPHERE);
        params.setValue(AVKey.NUM_LEVELS, 16);
        params.setValue(AVKey.NUM_EMPTY_LEVELS, 6);
        params.setValue(AVKey.TILE_WIDTH, 512);
        params.setValue(AVKey.TILE_HEIGHT, 512);
        params.setValue(AVKey.DATA_CACHE_NAME, "Earth/Bing");
        params.setValue(AVKey.DATASET_NAME, "ve");
        params.setValue(AVKey.FORMAT_SUFFIX, ".png");
        params.setValue(AVKey.TILE_ORIGIN, new LatLon(Angle.NEG90, Angle.NEG180));
        return new LevelSet(params);
    }

    @Test
    public void testEmptyLevelsHaveNoFolder()
    {
        LevelSet levels = createBingLikeLevelSet();
        for (int i = 0; i < 6; i++)
        {
            assertEquals("level " + i + " should have no cache folder",
                CacheLevels.NO_FOLDER, CacheLevels.folderNameOf(levels, i));
        }
    }

    @Test
    public void testFolderNamesSkipEmptyLevels()
    {
        LevelSet levels = createBingLikeLevelSet();
        assertEquals(0, CacheLevels.folderNameOf(levels, 6));
        assertEquals(5, CacheLevels.folderNameOf(levels, 11));
        assertEquals(9, CacheLevels.folderNameOf(levels, 15));
    }

    @Test
    public void testAvailableFolderRange()
    {
        LevelSet levels = createBingLikeLevelSet();
        assertEquals(0, CacheLevels.minAvailableFolder(levels));
        assertEquals(9, CacheLevels.maxAvailableFolder(levels));
    }

    @Test
    public void testRequestedRangeIsClippedToWhatTheLayerHas()
    {
        LevelSet levels = createBingLikeLevelSet();
        int[] range = CacheLevels.effectiveFolderRange(levels, 5, 14);
        assertNotNull(range);
        assertEquals(5, range[0]);
        assertEquals(9, range[1]);
        assertEquals("5-9", CacheLevels.describeFolderRange(range));
    }

    @Test
    public void testRequestedRangeEntirelyAboveAvailable()
    {
        LevelSet levels = createBingLikeLevelSet();
        assertNull(CacheLevels.effectiveFolderRange(levels, 12, 14));
        assertEquals("none", CacheLevels.describeFolderRange(null));
    }

    @Test
    public void testSingleFolderRangeDescription()
    {
        LevelSet levels = createBingLikeLevelSet();
        assertEquals("7", CacheLevels.describeFolderRange(CacheLevels.effectiveFolderRange(levels, 7, 7)));
    }

    @Test
    public void testHighestLevelNumberAtOrBelow()
    {
        LevelSet levels = createBingLikeLevelSet();
        // Folder 14 does not exist, so the finest level in range is folder 9, which is level 15.
        assertEquals(15, CacheLevels.highestLevelNumberAtOrBelow(levels, 14));
        assertEquals(11, CacheLevels.highestLevelNumberAtOrBelow(levels, 5));
        assertEquals(-1, CacheLevels.highestLevelNumberAtOrBelow(levels, -1));
    }

    @Test
    public void testIsFolderInRange()
    {
        LevelSet levels = createBingLikeLevelSet();
        assertFalse("level 10 is folder 4, below the range", CacheLevels.isFolderInRange(levels, 10, 5, 14));
        assertTrue("level 11 is folder 5, the first in range", CacheLevels.isFolderInRange(levels, 11, 5, 14));
        assertTrue("level 15 is folder 9, still in range", CacheLevels.isFolderInRange(levels, 15, 5, 14));
        assertFalse("empty levels are never in range", CacheLevels.isFolderInRange(levels, 3, 0, 14));
    }
}
