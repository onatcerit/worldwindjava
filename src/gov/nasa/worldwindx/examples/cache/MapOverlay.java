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
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.render.AnnotationAttributes;
import gov.nasa.worldwind.render.GlobeAnnotation;
import gov.nasa.worldwind.render.Renderable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A group of renderables the user placed on the globe - one distance measurement, one point, or one freehand drawing -
 * together with the small delete control that stays on the map next to it.
 * <p>
 * Overlays are owned by a {@link MapOverlayManager}, which puts the delete control in a pickable layer and removes the
 * whole group when that control is clicked.
 * </p>
 *
 * @author Cursor Agent
 */
public class MapOverlay
{
    /** Overlay kind for distance measurements. */
    public static final String MEASUREMENT = "Measurement";
    /** Overlay kind for single points. */
    public static final String POINT = "Point";
    /** Overlay kind for freehand drawings. */
    public static final String DRAWING = "Drawing";

    /** Multiplication sign drawn inside the delete control, built from its code point so the source stays ASCII. */
    protected static final String DELETE_GLYPH = String.valueOf((char) 0x00D7);

    protected final String kind;
    protected final List<Renderable> renderables = new ArrayList<Renderable>();
    protected final GlobeAnnotation deleteControl;
    protected String description = "";

    /**
     * Creates an overlay whose delete control sits at the given position.
     *
     * @param kind            one of {@link #MEASUREMENT}, {@link #POINT} or {@link #DRAWING}.
     * @param controlPosition where the delete control is anchored on the globe.
     */
    public MapOverlay(String kind, Position controlPosition)
    {
        if (kind == null)
        {
            throw new IllegalArgumentException("Overlay kind is required");
        }

        this.kind = kind;
        this.deleteControl = new GlobeAnnotation(DELETE_GLYPH,
            controlPosition != null ? controlPosition : Position.ZERO, createDeleteControlAttributes());
        this.deleteControl.setAlwaysOnTop(true);
        this.deleteControl.setPickEnabled(true);
        // The manager identifies the overlay to delete from the picked object, so point the control back at us.
        this.deleteControl.setDelegateOwner(this);
    }

    public String getKind()
    {
        return this.kind;
    }

    public String getDescription()
    {
        return this.description;
    }

    public void setDescription(String description)
    {
        this.description = description != null ? description : "";
        this.deleteControl.setValue(AVKey.DISPLAY_NAME, this.kind + ": " + this.description);
    }

    public void addRenderable(Renderable renderable)
    {
        if (renderable != null)
        {
            this.renderables.add(renderable);
        }
    }

    public List<Renderable> getRenderables()
    {
        return Collections.unmodifiableList(this.renderables);
    }

    public GlobeAnnotation getDeleteControl()
    {
        return this.deleteControl;
    }

    public void setDeleteControlPosition(Position position)
    {
        if (position != null)
        {
            this.deleteControl.setPosition(position);
        }
    }

    /**
     * Builds the look of the small red delete button drawn on the globe. The scale is pinned so the button keeps the
     * same size no matter how far the eye is from it.
     *
     * @return attributes for the delete control annotation.
     */
    protected static AnnotationAttributes createDeleteControlAttributes()
    {
        AnnotationAttributes attrs = new AnnotationAttributes();
        attrs.setFrameShape(AVKey.SHAPE_RECTANGLE);
        attrs.setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
        attrs.setSize(new Dimension(0, 0));
        attrs.setInsets(new Insets(1, 7, 1, 7));
        attrs.setDrawOffset(new Point(0, 0));
        attrs.setBackgroundColor(new Color(200, 40, 40, 235));
        attrs.setTextColor(Color.WHITE);
        attrs.setBorderColor(Color.WHITE);
        attrs.setBorderWidth(1);
        attrs.setCornerRadius(3);
        attrs.setFont(Font.decode("Arial-BOLD-14"));
        attrs.setEffect(AVKey.TEXT_EFFECT_NONE);
        attrs.setLeader(AVKey.SHAPE_NONE);
        attrs.setDistanceMinScale(1);
        attrs.setDistanceMaxScale(1);
        return attrs;
    }
}
