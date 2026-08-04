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
import gov.nasa.worldwind.retrieve.BulkRetrievable;
import gov.nasa.worldwind.retrieve.BulkRetrievalThread;
import gov.nasa.worldwindx.examples.util.SectorSelector;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Interactive map tool: drag a rectangle on the globe, then download that sector into the WorldWind cache.
 * <p>
 * Uses the existing {@link SectorSelector} without modifying it. When the user releases the mouse after drawing a
 * sector, this tool confirms and starts downloads through {@link SectorCacheController}.
 * </p>
 *
 * @author Cursor Agent
 */
public class MapSectorCacheTool
{
    protected final Frame owner;
    protected final SectorCacheController controller;
    protected final SectorSelector selector;
    protected final SectorCacheProgressDialog progressDialog;
    protected final PropertyChangeListener sectorListener;
    protected boolean selecting;
    protected boolean waitingForRelease;
    protected Runnable selectionStateListener;

    public MapSectorCacheTool(Frame owner, SectorCacheController controller)
    {
        if (owner == null || controller == null)
        {
            throw new IllegalArgumentException("Owner and SectorCacheController are required");
        }

        this.owner = owner;
        this.controller = controller;
        this.progressDialog = new SectorCacheProgressDialog(owner);

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

    /**
     * Optional callback invoked whenever selection mode starts or stops.
     *
     * @param selectionStateListener runnable notified on EDT when {@link #isSelecting()} changes
     */
    public void setSelectionStateListener(Runnable selectionStateListener)
    {
        this.selectionStateListener = selectionStateListener;
    }

    public boolean isSelecting()
    {
        return this.selecting;
    }

    /**
     * Arms the sector selector so the next press-and-drag on the globe draws a rectangle.
     */
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

    /**
     * Cancels interactive selection and clears the drawn sector.
     */
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

        // While dragging, SectorSelector reports the growing sector. On mouse release it fires null.
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
            // Re-arm so the user can try again without pressing the button a second time.
            this.selector.disable();
            this.selector.enable();
            return;
        }

        List<BulkRetrievable> retrievables = this.controller.listBulkRetrievables();
        if (retrievables.isEmpty())
        {
            JOptionPane.showMessageDialog(this.owner,
                "No bulk-downloadable layers or elevation models are available.",
                "Map selection", JOptionPane.WARNING_MESSAGE);
            this.cancelSelection();
            return;
        }

        String message = "Download cache for the selected area?\n\n"
            + SectorCacheController.makeSectorDescription(sector)
            + "\n\nData sources: " + retrievables.size();
        int choice = JOptionPane.showConfirmDialog(this.owner, message, "Download selected area",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION)
        {
            // Keep selection mode active so the user can draw again.
            this.selector.disable();
            this.selector.enable();
            return;
        }

        List<BulkRetrievalThread> threads = this.controller.startDownloads(sector, retrievables, null);
        this.progressDialog.showDownloads(sector, threads);
        this.cancelSelection();
    }

    protected void notifySelectionStateChanged()
    {
        if (this.selectionStateListener != null)
        {
            this.selectionStateListener.run();
        }
    }
}
