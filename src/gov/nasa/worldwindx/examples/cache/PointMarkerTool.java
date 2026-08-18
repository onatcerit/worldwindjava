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
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.Offset;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.render.PointPlacemarkAttributes;

import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * Places numbered points on the globe.
 * <p>
 * While armed, every click drops a point labelled with its number and its latitude and longitude. The tool stays armed
 * so points can be placed one after another, and each point carries a red delete button on the map.
 * </p>
 *
 * @author Cursor Agent
 */
public class PointMarkerTool extends AbstractMapTool
{
    protected static final Color POINT_COLOR = new Color(240, 170, 20);

    protected int nextPointNumber = 1;
    protected Point pressScreenPoint;
    protected Position pressPosition;

    public PointMarkerTool(MapOverlayManager manager)
    {
        super(manager);
    }

    /**
     * Returns the number of points currently on the globe.
     *
     * @return the point count.
     */
    public int getPointCount()
    {
        return this.manager.getOverlayCount(MapOverlay.POINT);
    }

    /** Removes every point from the globe and restarts the numbering. */
    public void clearPoints()
    {
        this.manager.removeOverlays(MapOverlay.POINT);
        this.nextPointNumber = 1;
    }

    @Override
    public void mousePressed(MouseEvent e)
    {
        if (!this.armed || e.getButton() != MouseEvent.BUTTON1 || e.isConsumed() || this.isOverOverlayControl())
        {
            return;
        }

        this.pressScreenPoint = e.getPoint();
        this.pressPosition = this.wwd.getCurrentPosition();
        e.consume();
    }

    @Override
    public void mouseDragged(MouseEvent e)
    {
        if (!this.armed || this.pressPosition == null || e.isConsumed())
        {
            return;
        }

        // Swallow the drag so the globe does not spin away while the tool is armed.
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e)
    {
        if (!this.armed || e.getButton() != MouseEvent.BUTTON1 || this.pressPosition == null || e.isConsumed())
        {
            return;
        }

        // A drag is not a placement; it is the user changing their mind.
        if (!this.isDrag(this.pressScreenPoint, e.getPoint()))
        {
            this.addPoint(this.pressPosition);
        }

        this.pressPosition = null;
        this.pressScreenPoint = null;
        e.consume();
    }

    /**
     * Adds a point at the given position.
     *
     * @param position where to put the point. Ignored when null.
     *
     * @return the overlay holding the new point, or null when nothing was added.
     */
    public MapOverlay addPoint(Position position)
    {
        if (position == null)
        {
            return null;
        }

        Position groundPosition = new Position(position, 0);
        String label = "P" + this.nextPointNumber++ + "  " + formatLatLon(position);

        MapOverlay overlay = new MapOverlay(MapOverlay.POINT, groundPosition);
        overlay.getDeleteControl().getAttributes().setDrawOffset(new Point(0, 26));

        PointPlacemark placemark = new PointPlacemark(groundPosition);
        placemark.setAltitudeMode(WorldWind.CLAMP_TO_GROUND);
        placemark.setLineEnabled(false);
        placemark.setAttributes(createPlacemarkAttributes());
        placemark.setLabelText(label);
        overlay.addRenderable(placemark);

        overlay.setDescription(label);
        this.manager.addOverlay(overlay);

        return overlay;
    }

    @Override
    protected void cancelInProgressWork()
    {
        this.pressPosition = null;
        this.pressScreenPoint = null;
    }

    protected static PointPlacemarkAttributes createPlacemarkAttributes()
    {
        PointPlacemarkAttributes attrs = new PointPlacemarkAttributes();
        attrs.setUsePointAsDefaultImage(true);
        attrs.setScale(9d);
        attrs.setLineMaterial(new Material(POINT_COLOR));
        attrs.setImageColor(POINT_COLOR);
        attrs.setLabelMaterial(Material.WHITE);
        attrs.setLabelFont(Font.decode("Arial-BOLD-12"));
        attrs.setLabelOffset(new Offset(0.9, 0.6, gov.nasa.worldwind.avlist.AVKey.FRACTION,
            gov.nasa.worldwind.avlist.AVKey.FRACTION));
        return attrs;
    }
}
