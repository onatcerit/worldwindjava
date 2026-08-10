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
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.Renderable;
import gov.nasa.worldwindx.examples.ApplicationTemplate;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds every measurement, point and drawing the user placed on the globe, and deletes one when its on-map delete
 * control is clicked.
 * <p>
 * Two layers are used. Shapes and labels go into a layer with picking turned off, so a click on a line or a label falls
 * through to the globe as usual. Only the small delete controls live in the pickable layer, which means a pick can only
 * ever mean "remove this overlay".
 * </p>
 *
 * @author Cursor Agent
 */
public class MapOverlayManager implements SelectListener
{
    protected final WorldWindow wwd;
    protected final RenderableLayer shapeLayer;
    protected final RenderableLayer controlLayer;
    protected final List<MapOverlay> overlays = new ArrayList<MapOverlay>();
    protected Runnable changeListener;
    protected boolean showDeleteControls = true;
    protected boolean overDeleteControl;
    protected Cursor baseCursor;

    public MapOverlayManager(WorldWindow wwd)
    {
        if (wwd == null)
        {
            throw new IllegalArgumentException("WorldWindow is required");
        }

        this.wwd = wwd;

        this.shapeLayer = new RenderableLayer();
        this.shapeLayer.setName("Map Tools");
        this.shapeLayer.setPickEnabled(false);

        this.controlLayer = new RenderableLayer();
        this.controlLayer.setName("Map Tool Controls");
        this.controlLayer.setPickEnabled(true);

        ApplicationTemplate.insertBeforeCompass(this.wwd, this.shapeLayer);
        ApplicationTemplate.insertBeforeCompass(this.wwd, this.controlLayer);

        this.wwd.addSelectListener(this);
    }

    public WorldWindow getWwd()
    {
        return this.wwd;
    }

    /**
     * Returns the non-pickable layer tools draw into, including their in-progress preview shapes.
     *
     * @return the layer holding shapes and labels.
     */
    public RenderableLayer getShapeLayer()
    {
        return this.shapeLayer;
    }

    /**
     * Sets a callback invoked whenever the set of overlays changes, so a caller can refresh button labels.
     *
     * @param changeListener the callback. May be null.
     */
    public void setChangeListener(Runnable changeListener)
    {
        this.changeListener = changeListener;
    }

    public List<MapOverlay> getOverlays()
    {
        return Collections.unmodifiableList(this.overlays);
    }

    public int getOverlayCount()
    {
        return this.overlays.size();
    }

    public int getOverlayCount(String kind)
    {
        int count = 0;
        for (MapOverlay overlay : this.overlays)
        {
            if (overlay.getKind().equals(kind))
            {
                count++;
            }
        }
        return count;
    }

    /**
     * Indicates whether the on-map delete controls are drawn.
     *
     * @return true when the delete controls are visible.
     */
    public boolean isShowDeleteControls()
    {
        return this.showDeleteControls;
    }

    /**
     * Shows or hides every on-map delete control without deleting the overlays themselves.
     *
     * @param showDeleteControls true to draw the delete controls.
     */
    public void setShowDeleteControls(boolean showDeleteControls)
    {
        if (this.showDeleteControls == showDeleteControls)
        {
            return;
        }

        this.showDeleteControls = showDeleteControls;
        for (MapOverlay overlay : this.overlays)
        {
            overlay.getDeleteControl().getAttributes().setVisible(showDeleteControls);
        }
        this.wwd.redraw();
    }

    public void addOverlay(MapOverlay overlay)
    {
        if (overlay == null || this.overlays.contains(overlay))
        {
            return;
        }

        for (Renderable renderable : overlay.getRenderables())
        {
            this.shapeLayer.addRenderable(renderable);
        }
        overlay.getDeleteControl().getAttributes().setVisible(this.showDeleteControls);
        this.controlLayer.addRenderable(overlay.getDeleteControl());

        this.overlays.add(overlay);
        this.notifyChanged();
        this.wwd.redraw();
    }

    public void removeOverlay(MapOverlay overlay)
    {
        if (overlay == null || !this.overlays.remove(overlay))
        {
            return;
        }

        for (Renderable renderable : overlay.getRenderables())
        {
            this.shapeLayer.removeRenderable(renderable);
        }
        this.controlLayer.removeRenderable(overlay.getDeleteControl());

        this.notifyChanged();
        this.wwd.redraw();
    }

    /**
     * Removes every overlay of a kind.
     *
     * @param kind one of the kind constants on {@link MapOverlay}, or null to remove all overlays.
     */
    public void removeOverlays(String kind)
    {
        List<MapOverlay> doomed = new ArrayList<MapOverlay>();
        for (MapOverlay overlay : this.overlays)
        {
            if (kind == null || overlay.getKind().equals(kind))
            {
                doomed.add(overlay);
            }
        }

        for (MapOverlay overlay : doomed)
        {
            this.removeOverlay(overlay);
        }
    }

    public void selected(SelectEvent event)
    {
        if (event == null || event.isConsumed())
        {
            return;
        }

        Object top = event.getTopObject();

        if (event.getEventAction().equals(SelectEvent.ROLLOVER))
        {
            boolean overControl = top instanceof MapOverlay;
            if (overControl != this.overDeleteControl)
            {
                this.overDeleteControl = overControl;
                this.applyCursor();
            }
            return;
        }

        if (!(top instanceof MapOverlay))
        {
            return;
        }

        if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)
            || event.getEventAction().equals(SelectEvent.LEFT_DOUBLE_CLICK))
        {
            this.removeOverlay((MapOverlay) top);
            this.overDeleteControl = false;
            this.applyCursor();
            event.consume();
        }
    }

    /**
     * Sets the cursor the globe shows when the mouse is not over a delete control. Tools set this while they are armed
     * so that hovering a delete control can temporarily override it and then restore it.
     *
     * @param baseCursor the cursor to fall back to. May be null for the default cursor.
     */
    public void setBaseCursor(Cursor baseCursor)
    {
        this.baseCursor = baseCursor;
        this.applyCursor();
    }

    protected void applyCursor()
    {
        Cursor cursor = this.overDeleteControl
            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : this.baseCursor;
        ((Component) this.wwd).setCursor(cursor);
    }

    protected void notifyChanged()
    {
        if (this.changeListener != null)
        {
            this.changeListener.run();
        }
    }
}
