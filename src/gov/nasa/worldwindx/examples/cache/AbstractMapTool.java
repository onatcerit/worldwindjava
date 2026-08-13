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

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.PositionEvent;
import gov.nasa.worldwind.event.PositionListener;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.pick.PickedObjectList;
import gov.nasa.worldwind.render.AnnotationAttributes;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.util.List;

/**
 * Shared plumbing for the on-globe tools: arming, the crosshair cursor, and the mouse and position listeners.
 * <p>
 * A tool is armed from a toolbar button. While armed it consumes the mouse gestures it understands so the globe does
 * not pan underneath the user, and it stays armed after finishing one item so several can be placed in a row.
 * </p>
 *
 * @author Cursor Agent
 */
public abstract class AbstractMapTool extends MouseAdapter implements MouseMotionListener, PositionListener
{
    /** Screen distance, in pixels, above which a press-release counts as a drag rather than a click. */
    protected static final int DRAG_THRESHOLD_PIXELS = 4;

    protected final MapOverlayManager manager;
    protected final WorldWindow wwd;
    protected boolean armed;
    protected Runnable armedStateListener;

    protected AbstractMapTool(MapOverlayManager manager)
    {
        if (manager == null)
        {
            throw new IllegalArgumentException("MapOverlayManager is required");
        }

        this.manager = manager;
        this.wwd = manager.getWwd();

        this.wwd.getInputHandler().addMouseListener(this);
        this.wwd.getInputHandler().addMouseMotionListener(this);
        this.wwd.addPositionListener(this);
    }

    public MapOverlayManager getManager()
    {
        return this.manager;
    }

    public boolean isArmed()
    {
        return this.armed;
    }

    public void setArmedStateListener(Runnable armedStateListener)
    {
        this.armedStateListener = armedStateListener;
    }

    public void setArmed(boolean armed)
    {
        if (this.armed == armed)
        {
            return;
        }

        this.armed = armed;
        this.cancelInProgressWork();
        this.manager.setBaseCursor(armed ? Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR) : null);

        if (this.armedStateListener != null)
        {
            this.armedStateListener.run();
        }
        this.wwd.redraw();
    }

    public void toggleArmed()
    {
        this.setArmed(!this.armed);
    }

    /** Drops whatever the user was in the middle of placing. Called when the tool is armed or disarmed. */
    protected abstract void cancelInProgressWork();

    public void mouseMoved(MouseEvent e)
    {
        // Required by MouseMotionListener. Cursor tracking happens through the position listener instead, because it
        // reports the geographic position under the cursor rather than the screen point.
    }

    /**
     * Swallows clicks while the tool is armed.
     * <p>
     * WorldWind binds VIEW_MOVE_TO to an unconsumed button-one click, so without this the globe flies to every point
     * the user places. Consuming the press and the release is not enough: the click arrives as its own event.
     * </p>
     *
     * @param e the click event.
     */
    @Override
    public void mouseClicked(MouseEvent e)
    {
        if (!this.armed || e.isConsumed() || this.isOverOverlayControl())
        {
            return;
        }

        e.consume();
    }

    public void moved(PositionEvent event)
    {
        // Overridden by tools that echo a shape under the cursor.
    }

    /**
     * Indicates whether the cursor is over an overlay's delete control.
     * <p>
     * WorldWind dispatches select events even for mouse events a listener consumed, so a click on a delete control
     * would otherwise both remove the overlay and place a new item. Tools use this to leave that click to the overlay
     * manager.
     * </p>
     *
     * @return true when the top picked object belongs to an overlay.
     */
    protected boolean isOverOverlayControl()
    {
        PickedObjectList picked = this.wwd.getObjectsAtCurrentPosition();
        return picked != null && picked.getTopObject() instanceof MapOverlay;
    }

    protected boolean isDrag(Point pressPoint, Point releasePoint)
    {
        return pressPoint != null && releasePoint != null
            && pressPoint.distance(releasePoint) > DRAG_THRESHOLD_PIXELS;
    }

    protected double computeDistanceMeters(LatLon start, LatLon end)
    {
        Angle distance = LatLon.greatCircleDistance(start, end);
        double radius = this.wwd.getModel().getGlobe().getRadiusAt(start.getLatitude(), start.getLongitude());
        return distance.radians * radius;
    }

    protected double computePathLengthMeters(List<? extends LatLon> positions)
    {
        double total = 0;
        for (int i = 1; i < positions.size(); i++)
        {
            total += this.computeDistanceMeters(positions.get(i - 1), positions.get(i));
        }
        return total;
    }

    protected static Position midPosition(Position start, Position end)
    {
        LatLon mid = LatLon.interpolateGreatCircle(0.5, start, end);
        return new Position(mid, 0.5 * (start.getElevation() + end.getElevation()));
    }

    /**
     * Builds the look of the white callout used for measurement and drawing labels.
     *
     * @param borderColor color of the label border, matching the shape it belongs to.
     *
     * @return attributes for a label annotation.
     */
    protected static AnnotationAttributes createLabelAttributes(Color borderColor)
    {
        AnnotationAttributes attrs = new AnnotationAttributes();
        attrs.setFrameShape(AVKey.SHAPE_RECTANGLE);
        attrs.setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
        attrs.setSize(new Dimension(0, 0));
        attrs.setInsets(new Insets(4, 8, 4, 8));
        attrs.setDrawOffset(new Point(0, 16));
        attrs.setBackgroundColor(new Color(255, 255, 255, 230));
        attrs.setTextColor(Color.BLACK);
        attrs.setBorderColor(borderColor);
        attrs.setBorderWidth(1.5);
        attrs.setCornerRadius(6);
        attrs.setFont(Font.decode("Arial-BOLD-13"));
        attrs.setEffect(AVKey.TEXT_EFFECT_NONE);
        attrs.setLeader(AVKey.SHAPE_NONE);
        attrs.setDistanceMinScale(1);
        attrs.setDistanceMaxScale(1);
        return attrs;
    }

    /**
     * Formats a distance in meters for display.
     *
     * @param meters the distance. Negative values are shown as a dash.
     *
     * @return the formatted distance.
     */
    public static String formatDistance(double meters)
    {
        if (meters < 0)
        {
            return "-";
        }
        if (meters < 1000)
        {
            return String.format("%.0f m", meters);
        }
        if (meters < 10000)
        {
            return String.format("%.2f km", meters / 1000.0);
        }
        return String.format("%.1f km", meters / 1000.0);
    }

    /**
     * Formats an area in square meters for display.
     *
     * @param squareMeters the area. Negative values are shown as a dash.
     *
     * @return the formatted area.
     */
    public static String formatArea(double squareMeters)
    {
        if (squareMeters < 0)
        {
            return "-";
        }
        if (squareMeters < 1e6)
        {
            return String.format("%,.0f m\u00B2", squareMeters);
        }
        return String.format("%,.2f km\u00B2", squareMeters / 1e6);
    }

    /**
     * Formats a geographic position as decimal degrees.
     *
     * @param position the position to format.
     *
     * @return the formatted latitude and longitude.
     */
    public static String formatLatLon(LatLon position)
    {
        if (position == null)
        {
            return "-";
        }

        return String.format(java.util.Locale.US, "%.5f\u00B0, %.5f\u00B0",
            position.getLatitude().degrees, position.getLongitude().degrees);
    }
}
