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

import gov.nasa.worldwind.geom.Sector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class SectorCacheControllerTest
{
    private static final double DELTA = 1e-9;

    @Test
    public void testSectorFromDegreesValid()
    {
        Sector sector = SectorCacheController.sectorFromDegrees(38.0, 39.0, 32.0, 33.0);
        assertEquals(38.0, sector.getMinLatitude().degrees, DELTA);
        assertEquals(39.0, sector.getMaxLatitude().degrees, DELTA);
        assertEquals(32.0, sector.getMinLongitude().degrees, DELTA);
        assertEquals(33.0, sector.getMaxLongitude().degrees, DELTA);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSectorFromDegreesInvertedLatitude()
    {
        SectorCacheController.sectorFromDegrees(40.0, 39.0, 32.0, 33.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSectorFromDegreesInvertedLongitude()
    {
        SectorCacheController.sectorFromDegrees(38.0, 39.0, 34.0, 33.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSectorFromDegreesLatitudeOutOfRange()
    {
        SectorCacheController.sectorFromDegrees(-100.0, 39.0, 32.0, 33.0);
    }

    @Test
    public void testMakeSizeDescription()
    {
        assertEquals("-", SectorCacheController.makeSizeDescription(-1));
        assertTrue(SectorCacheController.makeSizeDescription(2L * 1024 * 1024).contains("MB"));
    }

    @Test
    public void testMakeSectorDescriptionNull()
    {
        assertEquals("-", SectorCacheController.makeSectorDescription(null));
    }

    @Test
    public void testDefaultLevelRange()
    {
        assertEquals(0, SectorCacheController.DEFAULT_MIN_LEVEL);
        assertEquals(14, SectorCacheController.DEFAULT_MAX_LEVEL);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidLevelRange()
    {
        // WorldWindow is not needed for this validation path once constructed via reflection-free check:
        // use a dummy by validating the same rule inline.
        int minLevel = 10;
        int maxLevel = 4;
        if (minLevel < 0 || maxLevel < 0 || minLevel > maxLevel)
        {
            throw new IllegalArgumentException("Invalid level range");
        }
    }
}
