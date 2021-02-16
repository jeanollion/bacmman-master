package bacmman.ui.gui.configurationIO;

import bacmman.configuration.parameters.*;
import bacmman.core.Task;
import bacmman.data_structure.Selection;
import bacmman.data_structure.dao.MasterDAO;
import bacmman.data_structure.input_image.InputImages;
import bacmman.image.BoundingBox;
import bacmman.image.SimpleBoundingBox;
import bacmman.plugins.FeatureExtractor;
import bacmman.ui.gui.configuration.ConfigurationTreeGenerator;
import bacmman.utils.ArrayUtil;
import bacmman.utils.Triplet;

import javax.swing.*;
import java.awt.event.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ExtractRawDataset extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JList channelSelector;
    private JScrollPane parameterJSP;
    private final DefaultListModel<String> channelModel;
    private final FileChooser outputFile;
    private final GroupParameter container;
    enum FRAME_CHOICE_MODE {RANDOM, FLUO_SIGNAL}
    private final EnumChoiceParameter<FRAME_CHOICE_MODE> frameChoiceMode;
    private final ConditionalParameter frameChoiceCond;
    private final ChannelImageParameter channelImage;
    private final BoundedNumberParameter nFrames;
    private final GroupParameter bounds;
    private final BoundedNumberParameter xMin, xSize, yMin, ySize;
    private final ConfigurationTreeGenerator outputConfigTree;
    private final MasterDAO mDAO;
    private final List<String> selectedPositions;
    private Task resultingTask;

    public ExtractRawDataset(MasterDAO mDAO, List<String> selectedPositions) {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        this.mDAO = mDAO;
        this.selectedPositions=selectedPositions;
        channelModel = new DefaultListModel<>();
        this.channelSelector.setModel(channelModel);
        for (String channel : mDAO.getExperiment().getChannelImagesAsString(false)) channelModel.addElement(channel);
        outputFile = new FileChooser("Output File", FileChooser.FileChooserOption.FILE_ONLY, false)
                .setRelativePath(false)
                .mustExist(false)
                .setHint("Set file where dataset will be extracted. If file exists and is of same format, data will be appended to the file");
        frameChoiceMode = new EnumChoiceParameter<>("Frame Choice", FRAME_CHOICE_MODE.values(), FRAME_CHOICE_MODE.RANDOM);
        frameChoiceCond = new ConditionalParameter(frameChoiceMode);
        channelImage = new ChannelImageParameter("Channel Image", 0);
        channelImage.setIncludeDuplicatedChannels(false);
        frameChoiceCond.setActionParameters(FRAME_CHOICE_MODE.FLUO_SIGNAL, channelImage);
        nFrames = new BoundedNumberParameter("Number of frame per position", 0, 10, 1, null);

        xMin = new BoundedNumberParameter("X start", 0, 0, 0, null);
        xSize = new BoundedNumberParameter("X size", 0, 0, 0, null);
        yMin = new BoundedNumberParameter("Y start", 0, 0, 0, null);
        ySize = new BoundedNumberParameter("Y size", 0, 0, 0, null);
        bounds = new GroupParameter("Crop Image", xMin, xSize, yMin, ySize);
        container = new GroupParameter("", outputFile, frameChoiceCond, this.nFrames, bounds);
        container.setParent(mDAO.getExperiment());
        outputConfigTree = new ConfigurationTreeGenerator(mDAO.getExperiment(), container, v -> {
        }, (s, l) -> {
        }, s -> {
        }, null, null).showRootHandle(false);
        this.parameterJSP.setViewportView(outputConfigTree.getTree());

        channelSelector.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        channelSelector.addListSelectionListener(e -> setEnableOk());
        channelImage.addListener(e -> setEnableOk());
        buttonOK.addActionListener(e -> onOK());
        buttonCancel.addActionListener(e -> onCancel());
        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        setEnableOk();
    }

    private void setEnableOk() {
        if (channelSelector.getSelectedValuesList().isEmpty()) {
            buttonOK.setEnabled(false);
            return;
        }
        if (!container.isValid()) {
            buttonOK.setEnabled(false);
            return;
        }
        buttonOK.setEnabled(true);
    }

    private void setDefaultValues(String outputFile, int[] channels, BoundingBox bounds, FRAME_CHOICE_MODE mode, int nFrames) {
        if (outputFile != null) this.outputFile.setSelectedFilePath(outputFile);
        channelSelector.setSelectedIndices(channels);
        if (bounds!=null) {
            xMin.setValue(bounds.xMin());
            xSize.setValue(bounds.sizeX());
            yMin.setValue(bounds.yMin());
            ySize.setValue(bounds.sizeY());
        }
        this.frameChoiceMode.setValue(mode);
        this.nFrames.setValue(nFrames);
        outputConfigTree.getTree().updateUI();
    }

    private void onOK() {
        resultingTask = new Task(mDAO.getDBName(), mDAO.getDir().toFile().getAbsolutePath());
        int[] channels = channelSelector.getSelectedIndices();
        SimpleBoundingBox bounds = new SimpleBoundingBox(xMin.getValue().intValue(), xMin.getValue().intValue()+xSize.getValue().intValue()-1, yMin.getValue().intValue(), yMin.getValue().intValue()+ySize.getValue().intValue()-1, 0, 0);
        Map<String, List<Integer>> positionMapFrames = selectedPositions.stream().collect(Collectors.toMap(p->p, p->getFrames(mDAO.getExperiment().getPosition(p).getInputImages(), channelImage.getSelectedIndex())));
        resultingTask.setExtractRawDS(outputFile.getFirstSelectedFilePath(), channels, bounds, positionMapFrames);
        dispose();
    }
    private List<Integer> getFrames(InputImages images, int channel) {
        int nFrames = this.nFrames.getValue().intValue();
        switch (this.frameChoiceMode.getSelectedEnum()) {
            default:
            case RANDOM:
                List<Integer> choice = IntStream.range(0, images.getFrameNumber()).mapToObj(i->i).collect(Collectors.toList());
                Collections.shuffle(choice);
                return choice.stream().limit(nFrames).collect(Collectors.toList());
            case FLUO_SIGNAL:
                return InputImages.chooseNImagesWithSignal(images, channel, nFrames);
        }
    }
    public static Task promptExtractDatasetTask(MasterDAO mDAO, Task selectedTask, List<String> selectedPositions) {
        ExtractRawDataset dialog = new ExtractRawDataset(mDAO, selectedPositions);
        dialog.setTitle("Configure Dataset extraction");
        if (selectedTask != null) {
            int nFrames = 0;
            if (selectedTask.getExtractRawDSFrames() != null && !selectedTask.getExtractRawDSFrames().isEmpty()) nFrames = selectedTask.getExtractRawDSFrames().values().iterator().next().size();
            dialog.setDefaultValues(selectedTask.getExtractRawDSFile(), selectedTask.getExtractRawDSChannels(), selectedTask.getExtractRawDSBounds(), FRAME_CHOICE_MODE.RANDOM , nFrames);
        }
        dialog.pack();
        dialog.setVisible(true);
        return dialog.resultingTask;
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

}
