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
import gov.nasa.worldwindx.examples.util.SectorSelector;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Interactive map tool: drag a rectangle on the globe, then hand the selected sector to a shared cache dialog.
 * <p>
 * Uses the existing {@link SectorSelector} without modifying it. When the user releases the mouse after drawing a
 * sector, {@link SectorConsumer#accept(Sector)} is called so coordinates can be filled into
 * {@link CoordinateCacheDialog}.
 * </p>
 *
 * @author Cursor Agent
 */
public class MapSectorCacheTool
{
    /**
     * Receives a sector selected on the globe.
     */
    public interface SectorConsumer
    {
        void accept(Sector sector);
    }

    protected final Frame owner;
    protected final SectorCacheController controller;
    protected final SectorSelector selector;
    protected final PropertyChangeListener sectorListener;
    protected boolean selecting;
    protected boolean waitingForRelease;
    protected Runnable selectionStateListener;
    protected SectorConsumer sectorConsumer;

    public MapSectorCacheTool(Frame owner, SectorCacheController controller)
    {
        if (owner == null || controller == null)
        {
            throw new IllegalArgumentException("Owner and SectorCacheController are required");
        }

        this.owner = owner;
        this.controller = controller;

        this.selector = new SectorSelector(controller.getWwd());
        this.selector.setInteriorColor(new Color(1f, 1f, 1f, 0.12f));
        this.selector.setBorderColor(new Color(0.85f, 0.15f, 0.15f, 0.7f));
        this.selector.setBorderWidth(3);

        this.sectorListener = new PropertyChangeListener()
        {
            public void propertyChange(PropertyChangeEvent evt)
            {
                handleSectorPropertyChange(evt);
            }
        };
        this.selector.addPropertyChangeListener(SectorSelector.SECTOR_PROPERTY, this.sectorListener);
    }

    public void setSectorConsumer(SectorConsumer sectorConsumer)
    {
        this.sectorConsumer = sectorConsumer;
    }

    public void setSelectionStateListener(Runnable selectionStateListener)
    {
        this.selectionStateListener = selectionStateListener;
    }

    public boolean isSelecting()
    {
        return this.selecting;
    }

    public void startSelection()
    {
        if (this.selecting)
        {
            return;
        }

        this.controller.clearSectorPreview();
        this.waitingForRelease = false;
        this.selecting = true;
        this.selector.enable();
        this.notifySelectionStateChanged();
    }

    public void cancelSelection()
    {
        if (!this.selecting)
        {
            return;
        }

        this.waitingForRelease = false;
        this.selecting = false;
        this.selector.disable();
        this.notifySelectionStateChanged();
    }

    public void toggleSelection()
    {
        if (this.selecting)
        {
            this.cancelSelection();
        }
        else
        {
            this.startSelection();
        }
    }

    protected void handleSectorPropertyChange(PropertyChangeEvent evt)
    {
        if (!this.selecting)
        {
            return;
        }

        if (evt.getNewValue() instanceof Sector)
        {
            this.waitingForRelease = true;
            return;
        }

        if (evt.getNewValue() == null && this.waitingForRelease)
        {
            this.waitingForRelease = false;
            final Sector sector = this.selector.getSector();
            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    onSectorSelectionFinished(sector);
                }
            });
        }
    }

    protected void onSectorSelectionFinished(Sector sector)
    {
        if (!this.selecting)
        {
            return;
        }

        if (sector == null || sector.equals(Sector.EMPTY_SECTOR))
        {
            JOptionPane.showMessageDialog(this.owner,
                "No valid area was selected. Press and drag on the globe to draw a rectangle.",
                "Map selection", JOptionPane.WARNING_MESSAGE);
            this.selector.disable();
            this.selector.enable();
            return;
        }

        this.cancelSelection();
        if (this.sectorConsumer != null)
        {
            this.sectorConsumer.accept(sector);
        }
    }

    protected void notifySelectionStateChanged()
    {
        if (this.selectionStateListener != null)
        {
            this.selectionStateListener.run();
        }
    }
}
