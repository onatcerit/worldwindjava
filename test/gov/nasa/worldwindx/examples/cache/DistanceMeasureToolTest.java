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

import gov.nasa.worldwind.geom.LatLon;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

@RunWith(JUnit4.class)
public class DistanceMeasureToolTest
{
    @Test
    public void testFormatDistanceMeters()
    {
        assertEquals("250 m", DistanceMeasureTool.formatDistance(250));
    }

    @Test
    public void testFormatDistanceKilometers()
    {
        assertEquals("1.50 km", DistanceMeasureTool.formatDistance(1500));
        assertEquals("12.3 km", DistanceMeasureTool.formatDistance(12300));
    }

    @Test
    public void testFormatDistanceNegative()
    {
        assertEquals("-", DistanceMeasureTool.formatDistance(-1));
    }

    @Test
    public void testFormatAreaSquareMeters()
    {
        assertTrue(AbstractMapTool.formatArea(500000).endsWith(" m\u00B2"));
    }

    @Test
    public void testFormatAreaSquareKilometers()
    {
        assertTrue(AbstractMapTool.formatArea(5e6).endsWith(" km\u00B2"));
    }

    @Test
    public void testFormatAreaNegative()
    {
        assertEquals("-", AbstractMapTool.formatArea(-1));
    }

    @Test
    public void testFormatLatLon()
    {
        assertEquals("38.50000\u00B0, 32.25000\u00B0",
            AbstractMapTool.formatLatLon(LatLon.fromDegrees(38.5, 32.25)));
        assertEquals("-", AbstractMapTool.formatLatLon(null));
    }
}
