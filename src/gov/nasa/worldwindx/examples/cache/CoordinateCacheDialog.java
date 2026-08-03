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

import gov.nasa.worldwind.cache.BasicDataFileStore;
import gov.nasa.worldwind.event.BulkRetrievalEvent;
import gov.nasa.worldwind.event.BulkRetrievalListener;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.retrieve.BulkRetrievable;
import gov.nasa.worldwind.retrieve.BulkRetrievalThread;
import gov.nasa.worldwind.util.WWMath;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog that lets the user type geographic bounds and download that sector into the WorldWind cache.
 * <p>
 * This is the manual-entry path for offline cache preparation. Interactive map selection can later call the same
 * {@link SectorCacheController} with a sector produced by a selector.
 * </p>
 *
 * @author Cursor Agent
 */
public class CoordinateCacheDialog extends JDialog
{
    protected final SectorCacheController controller;
    protected Sector currentSector;

    protected JTextField minLatField;
    protected JTextField maxLatField;
    protected JTextField minLonField;
    protected JTextField maxLonField;
    protected JLabel sectorLabel;
    protected JLabel cacheLocationLabel;
    protected JPanel retrievablesPanel;
    protected JPanel monitorPanel;
    protected JButton startButton;
    protected final List<RetrievableRow> rows = new ArrayList<RetrievableRow>();

    public CoordinateCacheDialog(Frame owner, SectorCacheController controller)
    {
        super(owner, "Download Area by Coordinates", false);
        if (controller == null)
        {
            throw new IllegalArgumentException("SectorCacheController is required");
        }

        this.controller = controller;
        this.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e)
            {
                closeDialog();
            }
        });

        this.buildUi();
        this.pack();
        this.setMinimumSize(new Dimension(420, 480));
        this.setLocationRelativeTo(owner);
    }

    protected void buildUi()
    {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        this.getContentPane().add(root);

        root.add(this.createCoordinatePanel(), BorderLayout.NORTH);
        root.add(this.createCenterPanel(), BorderLayout.CENTER);
        root.add(this.createActionPanel(), BorderLayout.SOUTH);
    }

    protected JPanel createCoordinatePanel()
    {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder("Sector Coordinates (degrees)"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 3, 3, 3);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        this.minLatField = new JTextField("38.0");
        this.maxLatField = new JTextField("39.0");
        this.minLonField = new JTextField("32.0");
        this.maxLonField = new JTextField("33.0");

        int row = 0;
        this.addLabeledField(panel, c, row++, "Min latitude (S):", this.minLatField);
        this.addLabeledField(panel, c, row++, "Max latitude (N):", this.maxLatField);
        this.addLabeledField(panel, c, row++, "Min longitude (W):", this.minLonField);
        this.addLabeledField(panel, c, row++, "Max longitude (E):", this.maxLonField);

        JButton applyButton = new JButton("Apply sector");
        applyButton.setToolTipText("Validate coordinates, preview the sector on the globe, and enable download");
        applyButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                applySectorFromFields();
            }
        });

        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 2;
        panel.add(applyButton, c);
        row++;

        this.sectorLabel = new JLabel("No sector applied");
        this.sectorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        c.gridy = row;
        panel.add(this.sectorLabel, c);

        return panel;
    }

    protected void addLabeledField(JPanel panel, GridBagConstraints c, int row, String label, JTextField field)
    {
        c.gridx = 0;
        c.gridy = row;
        c.gridwidth = 1;
        c.weightx = 0;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    protected JPanel createCenterPanel()
    {
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel cachePanel = new JPanel(new BorderLayout(5, 5));
        cachePanel.setBorder(new TitledBorder("Cache"));
        this.cacheLocationLabel = new JLabel("Default WorldWind cache");
        JButton browseButton = new JButton("...");
        browseButton.setToolTipText("Choose a custom cache directory");
        browseButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                chooseCacheDirectory();
            }
        });
        cachePanel.add(this.cacheLocationLabel, BorderLayout.CENTER);
        cachePanel.add(browseButton, BorderLayout.EAST);
        cachePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, cachePanel.getPreferredSize().height + 20));
        center.add(cachePanel);

        this.retrievablesPanel = new JPanel();
        this.retrievablesPanel.setLayout(new BoxLayout(this.retrievablesPanel, BoxLayout.Y_AXIS));
        this.retrievablesPanel.setBorder(new TitledBorder("Data sources"));
        this.populateRetrievableRows();

        JScrollPane listScroll = new JScrollPane(this.retrievablesPanel);
        listScroll.setPreferredSize(new Dimension(380, 160));
        center.add(listScroll);

        this.monitorPanel = new JPanel();
        this.monitorPanel.setLayout(new BoxLayout(this.monitorPanel, BoxLayout.Y_AXIS));
        this.monitorPanel.setBorder(new TitledBorder("Downloads"));
        JPanel monitorWrapper = new JPanel(new BorderLayout());
        monitorWrapper.add(this.monitorPanel, BorderLayout.NORTH);
        JScrollPane monitorScroll = new JScrollPane(monitorWrapper);
        monitorScroll.setPreferredSize(new Dimension(380, 140));
        center.add(monitorScroll);

        return center;
    }

    protected JPanel createActionPanel()
    {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        this.startButton = new JButton("Start download");
        this.startButton.setEnabled(false);
        this.startButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                startDownload();
            }
        });

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                closeDialog();
            }
        });

        panel.add(this.startButton);
        panel.add(closeButton);
        return panel;
    }

    protected void populateRetrievableRows()
    {
        this.rows.clear();
        this.retrievablesPanel.removeAll();

        for (BulkRetrievable retrievable : this.controller.listBulkRetrievables())
        {
            RetrievableRow row = new RetrievableRow(retrievable);
            this.rows.add(row);
            this.retrievablesPanel.add(row);
        }

        this.retrievablesPanel.revalidate();
        this.retrievablesPanel.repaint();
    }

    protected void chooseCacheDirectory()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        int status = chooser.showOpenDialog(this);
        if (status == JFileChooser.APPROVE_OPTION)
        {
            File file = chooser.getSelectedFile();
            if (file != null)
            {
                this.controller.setCache(new BasicDataFileStore(file));
                this.cacheLocationLabel.setText(file.getPath());
                this.updateRetrievableEstimates();
            }
        }
    }

    protected void applySectorFromFields()
    {
        try
        {
            double minLat = this.parseDegree(this.minLatField.getText(), "Minimum latitude");
            double maxLat = this.parseDegree(this.maxLatField.getText(), "Maximum latitude");
            double minLon = this.parseDegree(this.minLonField.getText(), "Minimum longitude");
            double maxLon = this.parseDegree(this.maxLonField.getText(), "Maximum longitude");

            this.currentSector = SectorCacheController.sectorFromDegrees(minLat, maxLat, minLon, maxLon);
            this.controller.showSectorPreview(this.currentSector);
            this.sectorLabel.setText(SectorCacheController.makeSectorDescription(this.currentSector));
            this.startButton.setEnabled(true);
            this.updateRetrievableEstimates();
        }
        catch (IllegalArgumentException ex)
        {
            this.currentSector = null;
            this.controller.clearSectorPreview();
            this.sectorLabel.setText("No sector applied");
            this.startButton.setEnabled(false);
            this.updateRetrievableEstimates();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid coordinates", JOptionPane.ERROR_MESSAGE);
        }
    }

    protected double parseDegree(String text, String fieldName)
    {
        if (text == null || text.trim().isEmpty())
        {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        try
        {
            return Double.parseDouble(text.trim().replace(',', '.'));
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException(fieldName + " must be a number.");
        }
    }

    protected void updateRetrievableEstimates()
    {
        for (RetrievableRow row : this.rows)
        {
            row.updateEstimate(this.currentSector);
        }
    }

    protected void startDownload()
    {
        if (this.currentSector == null)
        {
            JOptionPane.showMessageDialog(this, "Apply a valid sector before starting the download.",
                "No sector", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ArrayList<BulkRetrievable> selected = new ArrayList<BulkRetrievable>();
        for (RetrievableRow row : this.rows)
        {
            if (row.isSelected())
            {
                selected.add(row.retrievable);
            }
        }

        if (selected.isEmpty())
        {
            JOptionPane.showMessageDialog(this, "Select at least one data source to download.",
                "No data source", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<BulkRetrievalThread> threads = this.controller.startDownloads(this.currentSector, selected,
            new BulkRetrievalListener()
            {
                public void eventOccurred(BulkRetrievalEvent event)
                {
                    // Intentionally empty: progress is tracked via BulkRetrievalThread.getProgress().
                }
            });

        for (BulkRetrievalThread thread : threads)
        {
            this.monitorPanel.add(new DownloadMonitorPanel(thread));
        }
        this.monitorPanel.revalidate();
        this.monitorPanel.repaint();
        this.validate();
    }

    public boolean hasActiveDownloads()
    {
        for (Component component : this.monitorPanel.getComponents())
        {
            if (component instanceof DownloadMonitorPanel
                && ((DownloadMonitorPanel) component).thread.isAlive())
            {
                return true;
            }
        }
        return false;
    }

    public void cancelActiveDownloads()
    {
        for (Component component : this.monitorPanel.getComponents())
        {
            if (component instanceof DownloadMonitorPanel)
            {
                DownloadMonitorPanel panel = (DownloadMonitorPanel) component;
                if (panel.thread.isAlive())
                {
                    panel.cancel();
                }
            }
        }
    }

    protected void closeDialog()
    {
        if (this.hasActiveDownloads())
        {
            int choice = JOptionPane.showConfirmDialog(this, "Cancel all active downloads?",
                "Active downloads", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.OK_OPTION)
            {
                return;
            }
            this.cancelActiveDownloads();
        }

        this.controller.clearSectorPreview();
        this.setVisible(false);
    }

    protected class RetrievableRow extends JPanel
    {
        protected final BulkRetrievable retrievable;
        protected final JCheckBox checkBox;
        protected final JLabel sizeLabel;
        protected Thread estimateThread;

        RetrievableRow(BulkRetrievable retrievable)
        {
            super(new BorderLayout(6, 0));
            this.retrievable = retrievable;
            this.setBorder(new EmptyBorder(2, 4, 2, 4));
            this.checkBox = new JCheckBox(retrievable.getName());
            this.sizeLabel = new JLabel("-");
            this.add(this.checkBox, BorderLayout.CENTER);
            this.add(this.sizeLabel, BorderLayout.EAST);

            this.checkBox.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    updateEstimate(currentSector);
                }
            });
        }

        boolean isSelected()
        {
            return this.checkBox.isSelected();
        }

        void updateEstimate(final Sector sector)
        {
            if (!this.checkBox.isSelected() || sector == null)
            {
                this.sizeLabel.setText("-");
                return;
            }

            if (this.estimateThread != null && this.estimateThread.isAlive())
            {
                return;
            }

            this.sizeLabel.setText("...");
            this.estimateThread = new Thread(new Runnable()
            {
                public void run()
                {
                    final long size = controller.estimateMissingDataSize(retrievable, sector);
                    SwingUtilities.invokeLater(new Runnable()
                    {
                        public void run()
                        {
                            sizeLabel.setText(SectorCacheController.makeSizeDescription(size));
                        }
                    });
                }
            }, "Cache-size-estimate");
            this.estimateThread.setDaemon(true);
            this.estimateThread.start();
        }
    }

    protected class DownloadMonitorPanel extends JPanel
    {
        protected final BulkRetrievalThread thread;
        protected final JLabel descriptionLabel;
        protected final JProgressBar progressBar;
        protected final JButton cancelButton;
        protected final Timer updateTimer;

        DownloadMonitorPanel(BulkRetrievalThread thread)
        {
            super();
            this.thread = thread;
            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            this.setBorder(new EmptyBorder(4, 4, 4, 4));

            this.descriptionLabel = new JLabel(thread.getRetrievable().getName());
            this.progressBar = new JProgressBar(0, 100);
            this.cancelButton = new JButton("Cancel");
            this.cancelButton.setBackground(Color.RED);
            this.cancelButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    if (DownloadMonitorPanel.this.thread.isAlive())
                    {
                        cancel();
                    }
                    else
                    {
                        Container parent = getParent();
                        if (parent != null)
                        {
                            parent.remove(DownloadMonitorPanel.this);
                            parent.revalidate();
                            parent.repaint();
                        }
                    }
                }
            });

            JPanel progressRow = new JPanel();
            progressRow.setLayout(new BoxLayout(progressRow, BoxLayout.X_AXIS));
            progressRow.add(this.progressBar);
            progressRow.add(Box.createHorizontalStrut(8));
            progressRow.add(this.cancelButton);

            this.add(this.descriptionLabel);
            this.add(progressRow);

            this.updateTimer = new Timer(1000, new ActionListener()
            {
                public void actionPerformed(ActionEvent e)
                {
                    updateStatus();
                }
            });
            this.updateTimer.start();
        }

        void cancel()
        {
            this.thread.interrupt();
            this.cancelButton.setText("Remove");
            this.cancelButton.setBackground(Color.ORANGE);
            this.updateTimer.stop();
        }

        protected void updateStatus()
        {
            long current = this.thread.getProgress().getCurrentSize();
            long total = this.thread.getProgress().getTotalSize();
            String name = this.thread.getRetrievable().getName();
            if (name.length() > 28)
            {
                name = name.substring(0, 25) + "...";
            }
            this.descriptionLabel.setText(name + " (" + SectorCacheController.makeSizeDescription(current)
                + " / " + SectorCacheController.makeSizeDescription(total) + ")");
            this.descriptionLabel.setToolTipText(SectorCacheController.makeSectorDescription(this.thread.getSector()));

            int percent = 0;
            if (this.thread.getProgress().getTotalCount() > 0)
            {
                percent = (int) WWMath.clamp(
                    (this.thread.getProgress().getCurrentCount() * 100.0)
                        / this.thread.getProgress().getTotalCount(), 0, 100);
            }
            this.progressBar.setValue(percent);

            if (!this.thread.isAlive())
            {
                this.cancelButton.setText("Remove");
                this.cancelButton.setBackground(Color.GREEN);
                this.updateTimer.stop();
            }
        }
    }
}
