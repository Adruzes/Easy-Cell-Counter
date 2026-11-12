/*
 * Easy Cell Counter  -  Step 1: 画像ローダー (Fiji / Groovy)
 *
 * ウィンドウに画像やフォルダをドラッグ＆ドロップ → リストに並ぶ
 * → ダブルクリック（または「開く」）で Bio-Formats でコンポジット表示。
 * CZI でも split / merge 不要。複数シーンがあれば選択ダイアログが出る。
 *
 * 置き場所: Fiji.app/scripts/Plugins/Easy_Cell_Counter.groovy
 */

import ij.IJ
import ij.ImagePlus
import ij.Prefs
import ij.CompositeImage
import ij.gui.GenericDialog
import ij.io.FileSaver
import ij.io.RoiEncoder
import ij.io.RoiDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import ij.gui.OvalRoi
import ij.gui.Line
import ij.gui.Arrow
import ij.gui.PolygonRoi
import ij.gui.PointRoi
import ij.plugin.Colors
import ij.gui.Overlay
import ij.gui.Roi
import ij.gui.TextRoi
import ij.gui.Toolbar
import ij.ImageListener
import ij.measure.ResultsTable
import ij.process.ImageStatistics
import ij.process.LUT
import ij.process.ColorProcessor
import ij.gui.ImageRoi
import ij.gui.ShapeRoi
import groovy.transform.CompileStatic
import org.scijava.Context
import org.scijava.command.CommandService
import net.imagej.DatasetService
import net.imagej.ImgPlus
import net.imglib2.img.display.imagej.ImageJFunctions
import ij.process.ImageProcessor
import ij.plugin.filter.MaximumFinder
import ij.plugin.filter.GaussianBlur
import ij.gui.DialogListener
import java.awt.AWTEvent
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.plaf.basic.BasicFileChooserUI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import ij.plugin.ZProjector
import loci.plugins.BF
import loci.plugins.in.ImporterOptions
import loci.formats.ImageReader
import loci.formats.MetadataTools

import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JRadioButton
import javax.swing.JTextField
import javax.swing.JComboBox
import javax.swing.JSlider
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.BasicStroke
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JOptionPane
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.WindowConstants
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.Insets
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent


/** リストに並べる1ファイル分 */
class ImageItem {
    File file
    boolean opened = false
    String toString() { (opened ? "✓ " : "    ") + file.name }
}


class EasyLoader {
    static final Set<String> EXTS = ['czi', 'tif', 'tiff', 'nd2', 'lif', 'lsm',
                                     'oib', 'oir', 'ims', 'vsi', 'png', 'jpg', 'jpeg'] as Set

    static final Color DROP_IDLE = new Color(245, 247, 250)
    static final Color DROP_HOVER = new Color(214, 234, 255)

    JFrame frame
    DefaultListModel<ImageItem> model = new DefaultListModel<>()
    JList<ImageItem> list
    JLabel dropLabel
    JLabel statusLabel
    JCheckBox zprojBox
    volatile boolean busy = false

    void show() {
        SwingUtilities.invokeLater { build() }
    }

    // ------------------------------------------------------------ UI
    void build() {
        frame = new JFrame("Easy Cell Counter")
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)

        def root = new JPanel(new BorderLayout(8, 8))
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10))

        // ドロップエリア
        dropLabel = new JLabel(
            "<html><center><b>ここに画像やフォルダを<br>ドラッグ＆ドロップ</b><br><br>" +
            "<font size='-1' color='#666666'>.czi / .tif / .nd2 / .lif など</font></center></html>",
            SwingConstants.CENTER)
        dropLabel.setFont(dropLabel.getFont().deriveFont(15f))
        dropLabel.setPreferredSize(new Dimension(380, 130))
        dropLabel.setOpaque(true)
        dropLabel.setBackground(DROP_IDLE)
        dropLabel.setBorder(BorderFactory.createDashedBorder(Color.GRAY, 2f, 8f, 5f, true))
        root.add(dropLabel, BorderLayout.NORTH)

        // ファイルリスト
        list = new JList<ImageItem>(model)
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        list.addMouseListener([mouseClicked: { MouseEvent e ->
            if (e.getClickCount() == 2) openSelected()
        }] as MouseAdapter)
        def scroll = new JScrollPane(list)
        scroll.setPreferredSize(new Dimension(380, 200))
        root.add(scroll, BorderLayout.CENTER)

        // 下部: オプション・ボタン・ステータス
        def bottom = new JPanel(new BorderLayout(4, 4))

        zprojBox = new JCheckBox("Zスタックは最大値投影して開く", true)
        bottom.add(zprojBox, BorderLayout.NORTH)

        def buttons = new JPanel(new GridLayout(1, 4, 6, 0))
        buttons.add(button("開く") { openSelected() })
        buttons.add(button("追加…") { chooseFiles() })
        buttons.add(button("削除") { removeSelected() })
        buttons.add(button("クリア") { model.clear(); setStatus("リストを空にしました") })
        bottom.add(buttons, BorderLayout.CENTER)

        statusLabel = new JLabel(" ")
        statusLabel.setForeground(Color.DARK_GRAY)
        bottom.add(statusLabel, BorderLayout.SOUTH)

        root.add(bottom, BorderLayout.SOUTH)

        // ウィンドウ全体をドロップ先にする
        def dropHandler = [
            dragEnter: { DropTargetDragEvent e -> setHover(true) },
            dragExit : { DropTargetEvent e -> setHover(false) },
            drop     : { DropTargetDropEvent e -> handleDrop(e) }
        ] as DropTargetAdapter
        new DropTarget(root, DnDConstants.ACTION_COPY, dropHandler, true)
        new DropTarget(list, DnDConstants.ACTION_COPY, dropHandler, true)
        new DropTarget(dropLabel, DnDConstants.ACTION_COPY, dropHandler, true)

        frame.setContentPane(root)
        frame.pack()
        frame.setLocationRelativeTo(null)
        frame.setVisible(true)
        setStatus("画像をドロップしてね")
    }

    JButton button(String text, Closure action) {
        def b = new JButton(text)
        b.addActionListener({ e -> action() } as ActionListener)
        return b
    }

    void setHover(boolean on) {
        dropLabel.setBackground(on ? DROP_HOVER : DROP_IDLE)
    }

    void setStatus(String msg) {
        SwingUtilities.invokeLater { statusLabel.setText(msg) }
        IJ.showStatus(msg)
    }

    // ------------------------------------------------------------ ファイル追加
    void handleDrop(DropTargetDropEvent e) {
        setHover(false)
        if (!e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            e.rejectDrop()
            setStatus("ファイル以外はドロップできません")
            return
        }
        e.acceptDrop(DnDConstants.ACTION_COPY)
        def files = e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor)
        addFiles(files as java.util.List<File>)
        e.dropComplete(true)
    }

    void chooseFiles() {
        def fc = new JFileChooser()
        fc.setMultiSelectionEnabled(true)
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES)
        fc.setDialogTitle("画像またはフォルダを選択")
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            addFiles(fc.getSelectedFiles() as java.util.List<File>)
        }
    }

    void addFiles(java.util.List<File> files) {
        int added = 0, skipped = 0
        files.each { File f ->
            if (f.isDirectory()) {
                (f.listFiles() ?: [] as File[]).sort { it.name.toLowerCase() }.each { File c ->
                    if (accept(c)) { if (addOne(c)) added++ }
                }
            } else if (accept(f)) {
                if (addOne(f)) added++
            } else {
                skipped++
            }
        }
        def msg = "${added} 個追加しました（全 ${model.size()} 個）"
        if (skipped > 0) msg += " / 対応していない形式を ${skipped} 個スキップ"
        setStatus(msg.toString())
        if (model.size() > 0 && list.getSelectedIndex() < 0) list.setSelectedIndex(0)
    }

    static boolean accept(File f) {
        if (!f.isFile() || f.name.startsWith(".")) return false
        def i = f.name.lastIndexOf('.')
        return i >= 0 && EXTS.contains(f.name.substring(i + 1).toLowerCase())
    }

    boolean addOne(File f) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).file.getCanonicalPath() == f.getCanonicalPath()) return false
        }
        model.addElement(new ImageItem(file: f))
        return true
    }

    void removeSelected() {
        int i = list.getSelectedIndex()
        if (i < 0) return
        model.remove(i)
        if (model.size() > 0) list.setSelectedIndex(Math.min(i, model.size() - 1))
    }

    // ------------------------------------------------------------ 画像を開く
    void openSelected() {
        ImageItem item = list.getSelectedValue()
        if (item == null) { setStatus("リストから画像を選んでね"); return }
        if (busy) { setStatus("いま別の画像を開いているところです"); return }
        busy = true
        setStatus("開いています: ${item.file.name}".toString())
        Thread.start {
            try {
                ImagePlus imp = openImage(item.file)
                if (imp != null) {
                    item.opened = true
                    IJ.showStatus("開きました: ${imp.getTitle()}".toString())
                    // ランチャーを閉じて、カウント用ウィンドウに切り替える
                    SwingUtilities.invokeLater {
                        frame.dispose()
                        new CounterWindow(file: item.file, imp: imp).show()
                    }
                } else {
                    setStatus("キャンセルしました")
                }
            } catch (Throwable t) {
                setStatus("開けませんでした: ${t.getMessage()}".toString())
                IJ.handleException(t)
            } finally {
                busy = false
            }
        }
    }

    ImagePlus openImage(File f) {
        int series = chooseSeries(f)
        if (series < 0) return null

        def opts = new ImporterOptions()
        opts.setId(f.getAbsolutePath())
        opts.setColorMode(ImporterOptions.COLOR_MODE_COMPOSITE)
        opts.setAutoscale(true)
        opts.setOpenAllSeries(false)
        opts.clearSeries()
        opts.setSeriesOn(series, true)

        ImagePlus[] imps = BF.openImagePlus(opts)
        if (imps == null || imps.length == 0) throw new IllegalStateException("画像を読み込めませんでした")
        ImagePlus imp = imps[0]
        // Bio-Formats がファイル（CZI など）のメタデータから読んだスケール。
        // 投影やコンポジット化で落ちないよう、最後にもう一度付け直す
        def cal0 = imp.getCalibration().copy()

        if (zprojBox.isSelected() && imp.getNSlices() > 1) {
            ImagePlus proj = ZProjector.run(imp, "max")
            proj.setTitle(imp.getTitle() + " (MAX)")
            imp.close()
            imp = proj
        }
        if (imp.getNChannels() > 1 && !imp.isComposite()) {
            imp = new CompositeImage(imp, IJ.COMPOSITE)
        }
        imp.setCalibration(cal0)
        if (imp.isComposite()) {
            ((CompositeImage) imp).setMode(IJ.COMPOSITE)
        }
        imp.show()
        return imp
    }

    /** シリーズ（CZIのシーンや解像度）が複数あれば選ばせる。キャンセル時は -1 */
    int chooseSeries(File f) {
        def reader = new ImageReader()
        def meta = MetadataTools.createOMEXMLMetadata()
        reader.setMetadataStore(meta)
        try {
            reader.setId(f.getAbsolutePath())
            int n = reader.getSeriesCount()
            if (n <= 1) return 0
            String[] labels = (0..<n).collect { int i ->
                reader.setSeries(i)
                def name = meta.getImageName(i) ?: "Series ${i + 1}"
                "#${i + 1} ${name}  (${reader.getSizeX()}x${reader.getSizeY()}, " +
                    "C${reader.getSizeC()} Z${reader.getSizeZ()} T${reader.getSizeT()})"
            }.collect { it.toString() } as String[]
            def gd = new GenericDialog("開く画像を選択")
            gd.addMessage(f.name + " には複数の画像（シーン・解像度など）が入っています")
            gd.addChoice("画像", labels, labels[0])
            gd.showDialog()
            if (gd.wasCanceled()) return -1
            return gd.getNextChoiceIndex()
        } finally {
            reader.close()
        }
    }
}


/** カウントした1点 */
class EccMarker {
    double x, y
    int type   // 0..7
    int z, t   // 1-based
}


/** B&C 用のヒストグラム表示 */
class HistogramPanel extends JPanel {
    int[] hist = new int[0]
    double hMin = 0, hMax = 1      // ヒストグラムの横軸範囲（データの最小・最大）
    double dMin = 0, dMax = 1      // 現在の表示範囲
    Color barColor = Color.GRAY

    HistogramPanel() {
        setPreferredSize(new Dimension(170, 80))
        setMinimumSize(new Dimension(120, 60))
        setBackground(Color.WHITE)
        setBorder(BorderFactory.createLineBorder(Color.GRAY))
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g)
        int w = getWidth() - 2, h = getHeight() - 2
        if (hist.length == 0 || w <= 0 || h <= 0) return

        // 最大の山だけ突出していると全体が潰れるので、2番目の山基準で縦軸を決める
        def sorted = (hist as java.util.List).sort(false).reverse()
        double top = sorted[0]
        if (sorted.size() > 1 && sorted[1] > 0 && sorted[0] > 3 * sorted[1]) top = sorted[1] * 1.5
        if (top <= 0) top = 1

        g.setColor(barColor)
        for (int px = 0; px < w; px++) {
            int bin = (int) (px * hist.length / (double) w)
            int bh = (int) Math.min(h, hist[bin] / top * h)
            g.drawLine(px + 1, h + 1, px + 1, h + 1 - bh)
        }

        // 表示範囲（min → max の伝達直線）
        double span = (hMax - hMin) ?: 1
        int x0 = 1 + (int) ((dMin - hMin) / span * w)
        int x1 = 1 + (int) ((dMax - hMin) / span * w)
        ((Graphics2D) g).setStroke(new BasicStroke(1.2f))
        g.setColor(Color.BLACK)
        g.drawLine(x0, h + 1, x1, 1)
    }
}


/**
 * Step 2: カウント用ウィンドウ
 *  - 上部: 画像のフォルダ・ファイル名
 *  - 左: Counters（タイプ名編集・カウント）
 *  - 中: Actions
 *  - 右: Channels / B&C
 */
class CounterWindow {
    static final int N_TYPES = 8
    static final String PREF_KEY = "easycellcounter.typename."
    static final Color[] TYPE_COLORS = [
        new Color(0, 255, 255), new Color(255, 255, 0), new Color(255, 0, 255),
        new Color(0, 255, 0), new Color(255, 140, 0), new Color(255, 80, 80),
        new Color(120, 160, 255), new Color(255, 255, 255)] as Color[]
    static final double MARKER_R = 5.0
    static final int SL = 1000   // スライダーの分解能

    File file
    ImagePlus imp
    JFrame frame

    // カウント
    def markers = []            // EccMarker
    def radios = []
    def nameFields = []
    def countFields = []
    def visibleBoxes = []      // JCheckBox（タイプごとの表示・非表示）
    int[] counts = new int[N_TYPES]
    JCheckBox deleteModeBox, showMarkersBox, showNumbersBox, showAllBox, showRegionsBox

    // 画像との接続
    MouseAdapter canvasMouse
    ImageListener imgListener
    int pressX = -1, pressY = -1
    int oldTool = -1
    boolean syncing = false

    // Channels
    JComboBox<String> modeCombo
    def channelBoxes = []
    def channelSwatches = []

    // B&C
    JComboBox<String> bcChannelCombo
    HistogramPanel histPanel
    JLabel histMinLabel, histMaxLabel
    JSlider minSlider, maxSlider, brightSlider, contrastSlider
    double dataMin = 0, dataMax = 1
    def statsCache = [:]

    void show() {
        SwingUtilities.invokeLater { build() }
    }

    // ============================================================ 画面
    void build() {
        frame = new JFrame("Easy Cell Counter - " + file.getName())
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
        frame.addWindowListener([windowClosed: { WindowEvent e -> detach() }] as WindowAdapter)

        def root = new JPanel(new BorderLayout(6, 6))
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8))
        root.add(buildPathPanel(), BorderLayout.NORTH)

        def body = new JPanel(new BorderLayout(6, 0))
        def left = new JPanel(new BorderLayout(0, 6))
        left.add(buildCounters(), BorderLayout.NORTH)
        left.add(buildRegionPanel(), BorderLayout.CENTER)
        body.add(left, BorderLayout.WEST)
        body.add(buildActions(), BorderLayout.CENTER)

        def display = new JPanel()
        display.setLayout(new BoxLayout(display, BoxLayout.Y_AXIS))
        display.add(buildChannels())
        display.add(Box.createVerticalStrut(6))
        display.add(buildBC())
        display.add(Box.createVerticalGlue())
        body.add(display, BorderLayout.EAST)

        root.add(body, BorderLayout.CENTER)

        frame.setContentPane(root)
        frame.pack()
        placeNextToImage()
        frame.setVisible(true)

        attach()
        refreshDisplayPanels()
    }

    /** 画像ウィンドウの右隣に出す */
    void placeNextToImage() {
        def win = imp?.getWindow()
        if (win != null) {
            frame.setLocation(win.getX() + win.getWidth() + 8, win.getY())
        } else {
            frame.setLocationRelativeTo(null)
        }
    }

    // ------------------------------------------------------------ パス表示
    JPanel buildPathPanel() {
        def p = new JPanel(new GridBagLayout())
        p.setBorder(BorderFactory.createTitledBorder("画像の場所"))
        addPathRow(p, 0, "フォルダ", file.getParentFile()?.getAbsolutePath() ?: "")
        addPathRow(p, 1, "ファイル", file.getName())

        // スケール
        def c = new GridBagConstraints()
        c.gridx = 0
        c.gridy = 2
        c.anchor = GridBagConstraints.WEST
        c.insets = new Insets(2, 4, 2, 6)
        p.add(new JLabel("スケール"), c)

        def row = new JPanel(new BorderLayout(4, 0))
        scaleField = new JTextField(26)
        scaleField.setEditable(false)
        row.add(scaleField, BorderLayout.CENTER)
        def setBtn = new JButton("設定…")
        setBtn.setToolTipText("スケールを手で設定（ファイルに情報がなかったとき用）")
        setBtn.addActionListener({ e -> setScaleDialog() } as ActionListener)
        row.add(setBtn, BorderLayout.EAST)
        c = new GridBagConstraints()
        c.gridx = 1
        c.gridy = 2
        c.weightx = 1.0
        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = new Insets(2, 0, 2, 4)
        p.add(row, c)

        scaleFromFile = imp.getCalibration().scaled()
        refreshScale()
        return p
    }

    // ------------------------------------------------------------ スケール
    JTextField scaleField
    boolean scaleFromFile = false
    boolean scaleManual = false

    /** ImageJ の単位名を表示用に（micron → µm） */
    static String unitLabel(String u) {
        if (u == null) return "px"
        def l = u.toLowerCase()
        if (l in ["micron", "microns", "um", "\u00b5m"]) return "\u00b5m"
        return u
    }

    static String num4(double v) {
        return String.format(Locale.ROOT, v >= 1 ? "%.3f" : "%.4f", v)
    }

    String scaleText() {
        def cal = imp.getCalibration()
        if (!cal.scaled()) return "未設定（px のまま）"
        def u = unitLabel(cal.getUnit())
        def sb = new StringBuilder()
        sb << num4(cal.pixelWidth)
        if (Math.abs(cal.pixelHeight - cal.pixelWidth) > 1e-9) sb << " × " << num4(cal.pixelHeight)
        sb << " " << u << "/px"
        sb << "　（画像 " << String.format(Locale.ROOT, "%.1f × %.1f", imp.getWidth() * cal.pixelWidth,
                                                     imp.getHeight() * cal.pixelHeight) << " " << u << "）"
        if (imp.getNSlices() > 1) sb << "　Z " << num4(cal.pixelDepth) << " " << u
        sb << (scaleManual ? "　［手動］" : (scaleFromFile ? "　［ファイルから取得］" : ""))
        return sb.toString()
    }

    void refreshScale() {
        if (scaleField == null) return
        def txt = scaleText()
        scaleField.setText(txt)
        scaleField.setToolTipText(txt)
        scaleField.setCaretPosition(0)
        scaleField.setForeground(imp.getCalibration().scaled() ? Color.BLACK : new Color(180, 60, 0))
    }

    void setScaleDialog() {
        def cal = imp.getCalibration()
        def gd = new GenericDialog("スケールを設定")
        gd.addNumericField("1 px の幅", cal.scaled() ? cal.pixelWidth : 1.0, 5)
        gd.addNumericField("1 px の高さ", cal.scaled() ? cal.pixelHeight : 1.0, 5)
        gd.addStringField("単位（µm なら micron）", cal.scaled() ? cal.getUnit() : "micron", 10)
        gd.addMessage("ファイルから正しく読めているときは変えなくて大丈夫です。\n" +
                      "変えると、リージョンの面積や密度もこのスケールで計算し直されます。")
        gd.showDialog()
        if (gd.wasCanceled()) return
        double pw = gd.getNextNumber(), ph = gd.getNextNumber()
        String unit = gd.getNextString().trim()
        if (gd.invalidNumber() || pw <= 0 || ph <= 0) {
            JOptionPane.showMessageDialog(frame, "正の数を入れてね")
            return
        }
        if (unit in ["µm", "\u00b5m", "um"]) unit = "micron"
        def nc = cal.copy()
        nc.pixelWidth = pw
        nc.pixelHeight = ph
        nc.setUnit(unit ?: "pixel")
        imp.setCalibration(nc)
        imp.repaintWindow()
        scaleManual = true
        refreshScale()
    }

    void addPathRow(JPanel p, int row, String label, String value) {
        def c = new GridBagConstraints()
        c.gridx = 0
        c.gridy = row
        c.anchor = GridBagConstraints.WEST
        c.insets = new Insets(2, 4, 2, 6)
        p.add(new JLabel(label), c)

        def tf = new JTextField(value, 26)
        tf.setEditable(false)
        tf.setToolTipText(value)
        tf.setCaretPosition(value.length())
        c = new GridBagConstraints()
        c.gridx = 1
        c.gridy = row
        c.weightx = 1.0
        c.fill = GridBagConstraints.HORIZONTAL
        c.insets = new Insets(2, 0, 2, 4)
        p.add(tf, c)
    }

    // ------------------------------------------------------------ Counters
    static String loadTypeName(int i) {
        return Prefs.get(PREF_KEY + i, "Type " + (i + 1))
    }

    static void saveTypeName(int i, String name) {
        Prefs.set(PREF_KEY + i, name)
    }

    String typeName(int i) {
        def s = nameFields[i].getText().trim()
        return s.isEmpty() ? "Type " + (i + 1) : s
    }

    int selectedType() {
        for (int i = 0; i < N_TYPES; i++) if (radios[i].isSelected()) return i
        return 0
    }

    JPanel buildCounters() {
        def p = new JPanel(new GridBagLayout())
        p.setBorder(BorderFactory.createTitledBorder("Counters"))
        def group = new ButtonGroup()
        for (int k = 0; k < N_TYPES; k++) {
            final int i = k
            def rb = new JRadioButton()
            rb.setSelected(i == 0)
            group.add(rb)
            radios << rb
            def c = new GridBagConstraints()
            c.gridx = 0
            c.gridy = i
            c.insets = new Insets(1, 2, 1, 0)
            p.add(rb, c)

            // マーカー色の見本
            p.add(swatch(TYPE_COLORS[i]), gbc(1, i, new Insets(1, 0, 1, 4)))

            def name = new JTextField(loadTypeName(i), 12)
            name.setToolTipText("クリックして名前を書き換え（例: tdTomato+(NeuN+)）")
            name.getDocument().addDocumentListener([
                insertUpdate : { DocumentEvent e -> saveTypeName(i, name.getText()) },
                removeUpdate : { DocumentEvent e -> saveTypeName(i, name.getText()) },
                changedUpdate: { DocumentEvent e -> saveTypeName(i, name.getText()) }
            ] as DocumentListener)
            name.addFocusListener([focusGained: { FocusEvent e -> rb.setSelected(true) }] as FocusAdapter)
            nameFields << name
            c = gbc(2, i, new Insets(1, 0, 1, 4))
            c.fill = GridBagConstraints.HORIZONTAL
            c.weightx = 1.0
            p.add(name, c)

            def count = new JTextField("0", 5)
            count.setEditable(false)
            count.setHorizontalAlignment(JTextField.CENTER)
            count.setBackground(Color.WHITE)
            countFields << count
            p.add(count, gbc(3, i, new Insets(1, 0, 1, 2)))

            // このタイプのマーカーを表示するか
            def vis = new JCheckBox()
            vis.setSelected(true)
            vis.setToolTipText("このタイプのマーカーを表示 / 非表示")
            vis.addActionListener({ e -> redrawMarkers() } as ActionListener)
            visibleBoxes << vis
            p.add(vis, gbc(4, i, new Insets(1, 0, 1, 2)))
        }
        def filler = gbc(0, N_TYPES, new Insets(0, 0, 0, 0))
        filler.weighty = 1.0
        p.add(Box.createVerticalGlue(), filler)
        return p
    }

    static GridBagConstraints gbc(int x, int y, Insets ins) {
        def c = new GridBagConstraints()
        c.gridx = x
        c.gridy = y
        c.anchor = GridBagConstraints.WEST
        c.insets = ins
        return c
    }

    static JLabel swatch(Color col) {
        def l = new JLabel()
        l.setOpaque(true)
        l.setBackground(col)
        l.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY))
        l.setPreferredSize(new Dimension(12, 12))
        return l
    }

    // ------------------------------------------------------------ Actions
    JPanel buildActions() {
        def p = new JPanel()
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS))
        p.setBorder(BorderFactory.createTitledBorder("Actions"))

        deleteModeBox = check("Delete Mode", false, true)
        deleteModeBox.setToolTipText("ONの間はクリックで近くのマーカーを削除（Shift+クリックでも削除できます）")
        showMarkersBox = check("Show Markers", true, true)
        showMarkersBox.setToolTipText("OFFにするとマーカーを隠して画像だけ見られます（隠している間はクリックしてもカウントしません）")
        showMarkersBox.addActionListener({ e ->
            redrawMarkers()
            IJ.showStatus(showMarkersBox.isSelected() ? "マーカーを表示しました" : "マーカーを隠しました（クリックでのカウントは止まります）")
        } as ActionListener)
        showNumbersBox = check("Show Numbers", true, true)
        showNumbersBox.addActionListener({ e -> redrawMarkers() } as ActionListener)
        showRegionsBox = check("Show Regions", true, true)
        showRegionsBox.setToolTipText("登録したリージョン（ROI）の枠を表示")
        showRegionsBox.addActionListener({ e -> redrawMarkers() } as ActionListener)
        showAllBox = check("Show All", false, true)
        showAllBox.setToolTipText("Zスタック・タイムラプスで、全スライスのマーカーを表示")
        showAllBox.addActionListener({ e -> redrawMarkers() } as ActionListener)

        def groups = [
            [header("Auto"),
             button("Auto Detect…", true) { autoDetect() }],
            [button("Undo", true) { undo() },
             button("Delete", true) { deleteLastOfSelected() }, deleteModeBox],
            [button("Results", true) { showResults() },
             button("Reset", true) { resetAll() }],
            [showMarkersBox, showNumbersBox, showAllBox],
            [header("Regions"),
             button("Add Region", true) { addRegion() },
             button("Remove Region…", true) { removeRegion() },
             button("Clear Regions", true) { clearRegions() },
             button("Remove Outside", true) { removeOutsideRegions() },
             showRegionsBox],
            [header("Lines"),
             button("Add Line", true) { addLine() },
             button("Remove Line", true) { removeLastLine() },
             button("Clear Lines", true) { clearLines() }],
            [header("Save"),
             button("Save All", true) { saveAll() },
             button("Markers", true) { saveMarkers() },
             button("Image", true) { saveImage() },
             button("B&C", true) { saveDisplay() },
             button("Lines", true) { saveLines() },
             button("Regions", true) { saveRegions() },
             button("Counts (CSV)", true) { exportCsv() }],
            [header("Load"),
             button("Markers", true) { loadMarkers() },
             button("B&C", true) { loadDisplay() },
             button("Lines", true) { loadLines() },
             button("Regions", true) { loadRegions() }]
        ]
        groups.eachWithIndex { g, gi ->
            if (gi > 0) p.add(Box.createVerticalStrut(10))
            g.each { comp ->
                comp.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT)
                p.add(comp)
                p.add(Box.createVerticalStrut(2))
            }
        }
        p.add(Box.createVerticalGlue())
        return p
    }

    static JButton button(String text, boolean enabled, Closure action = null) {
        def b = new JButton(text)
        b.setEnabled(enabled)
        if (action != null) b.addActionListener({ e -> action() } as ActionListener)
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height as int))
        return b
    }

    /** Actions 内の小見出し（Save / Load） */
    static JLabel header(String text) {
        def l = new JLabel(text)
        l.setFont(l.getFont().deriveFont(Font.BOLD))
        l.setBorder(BorderFactory.createEmptyBorder(0, 2, 2, 0))
        return l
    }

    static JCheckBox check(String text, boolean selected, boolean enabled) {
        def cb = new JCheckBox(text, selected)
        cb.setEnabled(enabled)
        return cb
    }

    // ============================================================ 画像との接続
    void attach() {
        def canvas = imp?.getCanvas()
        if (canvas == null) return
        oldTool = Toolbar.getToolId()
        IJ.setTool("hand")   // ドラッグで画面移動、クリックでカウント

        canvasMouse = [
            mousePressed : { MouseEvent e -> pressX = e.getX(); pressY = e.getY() },
            mouseReleased: { MouseEvent e -> onCanvasRelease(e) }
        ] as MouseAdapter
        canvas.addMouseListener(canvasMouse)

        imgListener = [
            imageOpened : { ImagePlus i -> },
            imageClosed : { ImagePlus i -> if (i.is(imp)) onImageClosed() },
            imageUpdated: { ImagePlus i ->
                if (i.is(imp) && !syncing) SwingUtilities.invokeLater { refreshDisplayPanels() }
            }
        ] as ImageListener
        ImagePlus.addImageListener(imgListener)
    }

    void detach() {
        if (canvasMouse != null) imp?.getCanvas()?.removeMouseListener(canvasMouse)
        canvasMouse = null
        if (imgListener != null) ImagePlus.removeImageListener(imgListener)
        imgListener = null
        if (oldTool >= 0) IJ.setTool(oldTool)
        oldTool = -1
    }

    void onImageClosed() {
        detach()
        SwingUtilities.invokeLater {
            frame.setTitle(frame.getTitle() + "（画像が閉じられました）")
        }
    }

    // ============================================================ カウント
    void onCanvasRelease(MouseEvent e) {
        int px = pressX, py = pressY
        pressX = pressY = -1
        if (px < 0 || Math.abs(e.getX() - px) > 3 || Math.abs(e.getY() - py) > 3) return  // ドラッグは移動
        if (!SwingUtilities.isLeftMouseButton(e)) return
        // 線や図形のツールを使っている間はカウントしない（ハンドツールに戻すとカウント再開）
        if (Toolbar.getToolId() != Toolbar.HAND) return
        def cv = imp.getCanvas()
        double x = cv.offScreenXD(e.getX())
        double y = cv.offScreenYD(e.getY())
        if (x < 0 || y < 0 || x >= imp.getWidth() || y >= imp.getHeight()) return
        // 隠している間は、見えないマーカーを誤って足したり消したりしないようにする
        if (!showMarkersBox.isSelected()) {
            IJ.showStatus("マーカーを隠しています。数えるときは Show Markers をONにしてね")
            return
        }
        boolean deleting = deleteModeBox.isSelected() || e.isShiftDown()
        if (!deleting && !visibleBoxes[selectedType()].isSelected()) {
            IJ.showStatus("${typeName(selectedType())} は非表示中です。数えるときは右のチェックをONにしてね".toString())
            return
        }
        if (deleting) {
            deleteNear(x, y, 10.0 / cv.getMagnification())
        } else {
            pushUndo()
            markers << new EccMarker(x: x, y: y, type: selectedType(), z: imp.getZ(), t: imp.getT())
            redrawMarkers()
        }
    }

    /** そのタイプのマーカーを表示しているか（Show Markers とタイプごとのチェックの両方） */
    boolean typeVisible(int t) {
        return showMarkersBox.isSelected() && visibleBoxes[t].isSelected()
    }

    boolean visibleNow(EccMarker m) {
        return showAllBox.isSelected() || (m.z == imp.getZ() && m.t == imp.getT())
    }

    void deleteNear(double x, double y, double tol) {
        EccMarker best = null
        double bd = tol * tol
        markers.each { EccMarker m ->
            if (!visibleNow(m) || !typeVisible(m.type)) return   // 隠しているマーカーは消さない
            double d = (m.x - x) * (m.x - x) + (m.y - y) * (m.y - y)
            if (d <= bd) { best = m; bd = d }
        }
        if (best != null) {
            pushUndo()
            markers.remove(best)
            redrawMarkers()
        }
    }

    void deleteLastOfSelected() {
        int t = selectedType()
        for (int i = markers.size() - 1; i >= 0; i--) {
            if (markers[i].type == t) {
                pushUndo()
                markers.remove(i)
                redrawMarkers()
                return
            }
        }
        IJ.showStatus("${typeName(t)} のマーカーはありません".toString())
    }

    void resetAll() {
        if (markers.isEmpty()) return
        int ans = JOptionPane.showConfirmDialog(frame,
            "マーカーを全部消しますか？（${markers.size()} 個）".toString(),
            "Reset", JOptionPane.YES_NO_OPTION)
        if (ans != JOptionPane.YES_OPTION) return
        pushUndo()
        markers.clear()
        redrawMarkers()
    }

    static final String MARKER_TAG = "ecc-marker"

    static boolean isMarkerRoi(Roi r) {
        return r != null && r.getName() != null && r.getName().startsWith(MARKER_TAG)
    }

    void redrawMarkers() {
        // 既存のオーバーレイから「自分のマーカー」だけ外して描き直す（線などはそのまま残す）
        def ov = imp.getOverlay() ?: new Overlay()
        for (int i = ov.size() - 1; i >= 0; i--) {
            if (isMarkerRoi(ov.get(i))) ov.remove(i)
        }
        boolean numbers = showNumbersBox.isSelected()
        def font = new Font("SansSerif", Font.BOLD, 12)
        def shown = markers.findAll { EccMarker m -> typeVisible(m.type) }
        shown.each { EccMarker m ->
            def col = TYPE_COLORS[m.type]
            def roi = new OvalRoi(m.x - MARKER_R, m.y - MARKER_R, 2 * MARKER_R, 2 * MARKER_R)
            roi.setStrokeColor(col)
            roi.setStrokeWidth(2)
            roi.setName(MARKER_TAG)
            position(roi, m)
            ov.add(roi)
            if (numbers) {
                def tr = new TextRoi(m.x + MARKER_R, m.y - MARKER_R - 14, String.valueOf(m.type + 1), font)
                tr.setStrokeColor(col)
                tr.setName(MARKER_TAG + "-label")
                position(tr, m)
                ov.add(tr)
            }
        }
        // 二値化マスクのプレビュー（半透明の黄色）
        if (previewMaskRoi != null) ov.add(previewMaskRoi)
        // 半自動カウントのプレビュー（点線・まだ確定していない）
        if (!previewMarkers.isEmpty()) {
            def dash = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                                       [3f, 3f] as float[], 0f)
            previewMarkers.each { EccMarker m ->
                def roi = new OvalRoi(m.x - MARKER_R, m.y - MARKER_R, 2 * MARKER_R, 2 * MARKER_R)
                roi.setStrokeColor(TYPE_COLORS[m.type])
                roi.setStroke(dash)
                roi.setName(MARKER_TAG + "-preview")
                position(roi, m)
                ov.add(roi)
            }
        }
        // リージョン（ROI）の枠と名前
        if (showRegionsBox.isSelected()) {
            def rfont = new Font("SansSerif", Font.BOLD, 14)
            regions.each { Roi reg ->
                def r = (Roi) reg.clone()
                r.setStrokeColor(REGION_COLOR)
                r.setStrokeWidth(1.5)
                r.setName(MARKER_TAG + "-region")
                ov.add(r)
                def label = reg.getName() ?: ""
                double[] cen = reg.getContourCentroid()
                def tr = new TextRoi(cen[0] - label.length() * 4.5, cen[1] - 9, label, rfont)
                tr.setStrokeColor(REGION_COLOR)
                tr.setName(MARKER_TAG + "-region-label")
                ov.add(tr)
            }
        }
        imp.setOverlay(ov)
        updateCounts()
    }

    /** 現在のZ・Tにだけ表示（Show All なら全スライス） */
    void position(Roi roi, EccMarker m) {
        if (showAllBox.isSelected()) return
        if (imp.isHyperStack()) {
            roi.setPosition(0, m.z, m.t)
        } else if (imp.getStackSize() > 1) {
            roi.setPosition(imp.getNSlices() > 1 ? m.z : m.t)
        }
    }

    void updateCounts() {
        counts = new int[N_TYPES]
        markers.each { EccMarker m -> counts[m.type]++ }
        for (int i = 0; i < N_TYPES; i++) countFields[i].setText(String.valueOf(counts[i]))
        refreshRegionTable()
    }

    void showResults() {
        def rt = new ResultsTable()
        for (int i = 0; i < N_TYPES; i++) {
            rt.incrementCounter()
            rt.addValue("Type", typeName(i))
            rt.addValue("Count", counts[i])
        }
        rt.show("Easy Cell Counter Results")
        if (!regions.isEmpty()) regionTable().show("Easy Cell Counter Regions")
    }

    // ============================================================ リージョン（ROI ごとのカウント）
    static final Color REGION_COLOR = new Color(255, 200, 0)
    def regions = []   // Roi（名前つき）

    /** 今の選択範囲をリージョンとして登録 */
    void addRegion() {
        def sel = imp.getRoi()
        if (sel == null || !sel.isArea()) {
            JOptionPane.showMessageDialog(frame,
                "先に四角・楕円・フリーハンドなどの範囲ツールで、数えたい領域を囲んでね。\n囲んだあとにこのボタンで登録できます。")
            return
        }
        def name = JOptionPane.showInputDialog(frame, "リージョンの名前", "R" + (regions.size() + 1))
        if (name == null) return
        name = name.trim() ?: "R" + (regions.size() + 1)
        if (regions.any { it.getName() == name }) {
            JOptionPane.showMessageDialog(frame, "「${name}」はもうあります。別の名前にしてね".toString())
            return
        }
        def reg = (Roi) sel.clone()
        reg.setName(name)
        reg.setPosition(0)        // すべてのスライスで有効
        regions << reg
        imp.deleteRoi()
        IJ.setTool("hand")
        redrawMarkers()
        IJ.showStatus("リージョン「${name}」を登録しました（全 ${regions.size()} 個）".toString())
    }

    void removeRegion() {
        if (regions.isEmpty()) { IJ.showStatus("リージョンはありません"); return }
        Object[] names = regions.collect { it.getName() } as Object[]
        def pick = JOptionPane.showInputDialog(frame, "削除するリージョン", "Remove Region",
            JOptionPane.QUESTION_MESSAGE, null, names, names[names.length - 1])
        if (pick == null) return
        regions.removeAll { it.getName() == pick }
        redrawMarkers()
    }

    void clearRegions() {
        if (regions.isEmpty()) return
        int ans = JOptionPane.showConfirmDialog(frame, "リージョンを全部消しますか？（${regions.size()} 個）".toString(),
            "Clear Regions", JOptionPane.YES_NO_OPTION)
        if (ans != JOptionPane.YES_OPTION) return
        regions.clear()
        redrawMarkers()
    }

    // ---- リージョンごとの表（Counters の下）
    DefaultTableModel regionModel
    JCheckBox densityBox
    JLabel regionHint

    JPanel buildRegionPanel() {
        def p = new JPanel(new BorderLayout(0, 4))
        p.setBorder(BorderFactory.createTitledBorder("Regions"))

        densityBox = new JCheckBox("1 mm² あたりの数で表示")
        densityBox.addActionListener({ e -> refreshRegionTable() } as ActionListener)
        p.add(densityBox, BorderLayout.NORTH)

        regionModel = new DefaultTableModel() {
            @Override boolean isCellEditable(int r, int c) { return false }
        }
        def table = new JTable(regionModel)
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF)
        table.setFillsViewportHeight(true)
        def sc = new JScrollPane(table)
        sc.setPreferredSize(new Dimension(320, 170))
        p.add(sc, BorderLayout.CENTER)

        regionHint = new JLabel(" ")
        regionHint.setForeground(Color.DARK_GRAY)
        p.add(regionHint, BorderLayout.SOUTH)
        return p
    }

    void refreshRegionTable() {
        if (regionModel == null) return
        def used = (0..<N_TYPES).findAll { counts[it] > 0 }
        boolean canDens = perMm2(1, 1.0d) != null
        densityBox.setEnabled(canDens)
        boolean dens = canDens && densityBox.isSelected()

        def cols = ["Region", "Area (" + areaUnit() + ")"] + used.collect { typeName(it as int) } + ["Total"]
        def data = []
        regionSummary().each { r ->
            data << regionRow(r[0] as String, r[2] as double, r[3] as int[], r[4] as int, used, dens)
        }
        def cal = imp.getCalibration()
        double whole = imp.getWidth() * imp.getHeight() * cal.pixelWidth * cal.pixelHeight
        int total = (counts as java.util.List).sum() as int
        data << regionRow("（画像全体）", whole, counts, total, used, dens)
        regionModel.setDataVector(data as Object[][], cols as Object[])

        regionHint.setText(regions.isEmpty() ?
            "範囲を囲んで Add Region を押すと、範囲ごとの数が出ます" :
            (dens ? "単位: 個 / mm²" : "単位: 個（マーカーを足したり消したりすると自動で更新）"))
    }

    Object[] regionRow(String name, double area, int[] c, int total, java.util.List used, boolean dens) {
        def row = [name, String.format(Locale.ROOT, "%.0f", area)]
        used.each { i ->
            int n = c[i as int]
            row << (dens ? String.format(Locale.ROOT, "%.1f", perMm2(n, area)) : String.valueOf(n))
        }
        row << (dens ? String.format(Locale.ROOT, "%.1f", perMm2(total, area)) : String.valueOf(total))
        return row as Object[]
    }

    boolean inAnyRegion(EccMarker m) {
        return regions.any { Roi r -> r.contains((int) m.x, (int) m.y) }
    }

    /** 先に全体を数えてから: リージョンの外にあるマーカーを消して、中だけ残す */
    void removeOutsideRegions() {
        if (regions.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "先に Add Region でリージョンを登録してね")
            return
        }
        def outside = markers.findAll { EccMarker m -> !inAnyRegion(m) }
        if (outside.isEmpty()) { IJ.showStatus("リージョンの外にマーカーはありません"); return }
        int ans = JOptionPane.showConfirmDialog(frame,
            "リージョンの外にあるマーカー ${outside.size()} 個を消して、中だけ残しますか？\n（Undo で元に戻せます）".toString(),
            "Remove Outside", JOptionPane.YES_NO_OPTION)
        if (ans != JOptionPane.YES_OPTION) return
        pushUndo()
        def ids = new IdentityHashMap()
        outside.each { ids.put(it, true) }
        markers.removeAll { ids.containsKey(it) }
        redrawMarkers()
        IJ.showStatus("${outside.size()} 個消しました（残り ${markers.size()} 個）".toString())
    }

    /** リージョンの面積（画素数） */
    static int regionPixels(Roi r) {
        return r.getContainedPoints().length
    }

    /** リージョンごとの集計: [名前, 画素数, 面積(校正単位), [タイプごとの数], 合計] */
    def regionSummary() {
        def cal = imp.getCalibration()
        double pxArea = cal.pixelWidth * cal.pixelHeight
        return regions.collect { Roi reg ->
            int[] c = new int[N_TYPES]
            markers.each { EccMarker m ->
                if (reg.contains((int) m.x, (int) m.y)) c[m.type]++
            }
            int npx = regionPixels(reg)
            [reg.getName(), npx, npx * pxArea, c, (c as java.util.List).sum() as int]
        }
    }

    String areaUnit() {
        def cal = imp.getCalibration()
        return cal.scaled() ? unitLabel(cal.getUnit()) + "^2" : "px^2"
    }

    /** 密度（1 mm² あたり）。長さの単位が µm / mm でないときは null */
    Double perMm2(int count, double area) {
        def u = imp.getCalibration().getUnit()?.toLowerCase()
        double mm2
        if (u in ["micron", "microns", "µm", "um", "\u00b5m"]) mm2 = area / 1e6
        else if (u in ["mm", "millimeter", "millimeters"]) mm2 = area
        else return null
        return mm2 > 0 ? count / mm2 : null
    }

    ResultsTable regionTable() {
        def rt = new ResultsTable()
        regionSummary().each { r ->
            rt.incrementCounter()
            rt.addValue("Region", r[0] as String)
            rt.addValue("Area (" + areaUnit() + ")", r[2] as double)
            for (int i = 0; i < N_TYPES; i++) {
                int c = (r[3] as int[])[i]
                if (c == 0 && counts[i] == 0) continue   // 一度も使っていないタイプは省略
                rt.addValue(typeName(i), c)
                def d = perMm2(c, r[2] as double)
                if (d != null) rt.addValue(typeName(i) + " /mm2", d as double)
            }
            rt.addValue("Total", r[4] as int)
        }
        return rt
    }

    // ---- 保存・読み込み（線と同じ XML 形式。ルート要素だけ違う）
    void saveRegions() {
        if (regions.isEmpty()) { JOptionPane.showMessageDialog(frame, "リージョンがまだありません"); return }
        File out = chooseSaveFileWithFormats("リージョンを保存", baseName() + "_regions", [
            ["XML", ".xml"],
            ["ImageJ ROIセット（ROI Manager で開ける）", ".zip"]
        ])
        if (out == null) return
        try {
            if (out.getName().toLowerCase().endsWith(".zip")) writeLinesZip(out, regions)
            else writeLinesXml(out, regions, "EasyCellCounter_Regions")
            JOptionPane.showMessageDialog(frame, "${regions.size()} 個のリージョンを保存しました\n${out.getAbsolutePath()}".toString())
        } catch (Exception ex) {
            IJ.handleException(ex)
        }
    }

    void loadRegions() {
        def fc = new JFileChooser(file.getParentFile())
        fc.setDialogTitle("リージョンを読み込み")
        fc.setFileFilter(new FileNameExtensionFilter("リージョン (*.xml, *.zip, *.roi)", "xml", "zip", "roi"))
        for (String ext : [".xml", ".zip"]) {
            def guess = new File(file.getParentFile(), baseName() + "_regions" + ext)
            if (guess.exists()) { fc.setSelectedFile(guess); break }
        }
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        File f = fc.getSelectedFile()
        def loaded = []
        try {
            def lower = f.getName().toLowerCase()
            if (lower.endsWith(".xml")) {
                loaded.addAll(readLinesXml(f, "EasyCellCounter_Regions")[0])
            } else if (lower.endsWith(".roi")) {
                def r = RoiDecoder.open(f.getAbsolutePath())
                if (r != null) loaded << r
            } else {
                def zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(f)))
                try {
                    def entry
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.getName().toLowerCase().endsWith(".roi")) continue
                        def r = RoiDecoder.openFromByteArray(zis.readAllBytes())
                        if (r != null) loaded << r
                    }
                } finally {
                    zis.close()
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "読み込めませんでした:\n" + ex.getMessage())
            return
        }
        loaded = loaded.findAll { (it as Roi).isArea() }
        if (loaded.isEmpty()) { JOptionPane.showMessageDialog(frame, "範囲（リージョン）が見つかりませんでした"); return }
        loaded.each { Roi r ->
            def name = r.getName()
            if (!name || regions.any { it.getName() == name }) name = "R" + (regions.size() + 1)
            r.setName(name)
            r.setPosition(0)
            regions << r
        }
        redrawMarkers()
        IJ.showStatus("${loaded.size()} 個のリージョンを読み込みました".toString())
    }

    // ============================================================ Undo
    def undoStack = []
    static final int UNDO_LIMIT = 100

    static EccMarker copyMarker(EccMarker m) {
        return new EccMarker(x: m.x, y: m.y, type: m.type, z: m.z, t: m.t)
    }

    /** マーカーを変更する直前に呼ぶ */
    void pushUndo() {
        undoStack << markers.collect { copyMarker(it as EccMarker) }
        if (undoStack.size() > UNDO_LIMIT) undoStack.remove(0)
    }

    void undo() {
        if (undoStack.isEmpty()) { IJ.showStatus("これ以上戻せません"); return }
        markers = undoStack.remove(undoStack.size() - 1)
        redrawMarkers()
        IJ.showStatus("元に戻しました（マーカー ${markers.size()} 個）".toString())
    }

    // ============================================================ 半自動カウント
    /*
     * 流れ:
     *  1. （任意）四角・フリーハンドなどの範囲ツールで、数えたい領域を囲む
     *  2. Auto Detect… でチャンネルとパラメータを選ぶ → 画像に点線でプレビュー
     *  3. OK で確定 → 間違いはクリック / Shift+クリック で手直し
     *
     * 検出: 選んだチャンネルをガウスぼかし → 局所最大（Find Maxima と同じ）を細胞として拾う
     * 背景マスク: 同じチャンネルを二値化（自動しきい値 or 手動）し、小さな粒を除いた「前景」
     *            の中にある局所最大だけを残す。背景ノイズを拾わないため。
     *            ※ 二値画像そのもので局所最大を探すと、くっついた核が1個の平らな塊になって
     *              分けられないので、検出は元の輝度で行い、マスクは「場所の制限」にだけ使う
     * 陽性フィルタ: 別チャンネルの点の周りの平均輝度がしきい値以上のものだけ残す
     *              （例: NeuN で核を拾って、tdTomato 陽性のものだけ残す）
     */
    static final String AD = "easycellcounter.auto."
    def previewMarkers = []
    int adChannel = (int) Prefs.get(AD + "channel", 1)
    double adSigma = Prefs.get(AD + "sigma", 1.5)
    double adProm = Prefs.get(AD + "prominence", 50)
    double adMinInt = Prefs.get(AD + "minIntensity", 0)
    int adFilterCh = (int) Prefs.get(AD + "filterChannel", 0)   // 0 = なし
    double adFilterThr = Prefs.get(AD + "filterThreshold", 0)
    int adFilterRadius = (int) Prefs.get(AD + "filterRadius", 3)
    boolean adFilterAuto = Prefs.get(AD + "filterAuto", false)
    boolean adReplace = Prefs.get(AD + "replace", true)
    boolean adPreview = true
    int adType = 0
    // 背景マスク（二値化）
    static final String[] MASK_METHODS = ["Li", "Triangle", "Otsu", "Huang", "Default", "Mean", "手動"] as String[]
    boolean adUseMask = Prefs.get(AD + "useMask", true)
    String adMaskMethod = Prefs.get(AD + "maskMethod", "Li")
    double adMaskThr = Prefs.get(AD + "maskThreshold", 0)
    int adMaskMinArea = (int) Prefs.get(AD + "maskMinArea", 30)
    // マスクのキャッシュ
    String maskKey = null
    byte[] maskData = null
    double maskThrUsed = Double.NaN
    Roi previewMaskRoi = null

    // ぼかした画像のキャッシュ（Prominence だけ変えたときに速くするため）
    String blurKey = null
    ImageProcessor blurred = null

    ImageProcessor blurredChannel(int ch, double sigma, int z, int t) {
        def key = "${ch},${sigma},${z},${t}".toString()
        if (key != blurKey) {
            def ip = imp.getStack().getProcessor(imp.getStackIndex(ch, z, t)).duplicate().convertToFloat()
            if (sigma > 0) new GaussianBlur().blurGaussian(ip, sigma, sigma, 0.01)
            blurred = ip
            blurKey = key
        }
        return blurred
    }

    /** 点 (x, y) の周り（半径 r の正方形）の平均輝度 */
    static double localMean(ImageProcessor ip, int x, int y, int r) {
        double sum = 0
        int n = 0
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int xx = x + dx, yy = y + dy
                if (xx >= 0 && yy >= 0 && xx < ip.getWidth() && yy < ip.getHeight()) {
                    sum += ip.getf(xx, yy)
                    n++
                }
            }
        }
        return n > 0 ? sum / n : 0
    }

    /**
     * 数える範囲:
     *   範囲ツールで囲んでいればその中
     *   囲んでいなくてリージョンを登録していれば、全リージョンを合わせた範囲
     *   どちらもなければ null（画像全体）
     */
    Roi countingArea() {
        def r = imp.getRoi()
        if (r != null && r.isArea()) return r
        if (!regions.isEmpty()) {
            ShapeRoi u = null
            regions.each { Roi reg ->
                def sr = new ShapeRoi((Roi) reg.clone())
                u = (u == null) ? sr : u.or(sr)
            }
            return u
        }
        return null
    }

    /** 二値化のしきい値（元データの値）。自動の場合は軽くぼかした画像から決める */
    double maskThreshold(ImageProcessor src) {
        if (adMaskMethod == "手動") return adMaskThr
        def tmp = src.duplicate()
        tmp.resetRoi()
        tmp.resetMinAndMax()
        tmp.setAutoThreshold(adMaskMethod + " dark")
        return tmp.getMinThreshold()
    }

    /** 検出チャンネルの前景マスク（1 = 細胞がありそうな場所）。設定が同じなら使い回す */
    byte[] currentMask(int z, int t) {
        def key = "${adChannel},${adMaskMethod},${adMaskThr},${adMaskMinArea},${z},${t}".toString()
        if (key != maskKey) {
            def src = imp.getStack().getProcessor(imp.getStackIndex(adChannel, z, t)).duplicate().convertToFloat()
            new GaussianBlur().blurGaussian(src, 1.0, 1.0, 0.01)   // 粒状ノイズでマスクが穴だらけにならないよう軽く
            maskThrUsed = maskThreshold(src)
            maskData = buildMask(src, maskThrUsed, adMaskMinArea)
            maskKey = key
            previewMaskRoi = null   // 表示用も作り直す
        }
        return maskData
    }

    /**
     * しきい値以上の画素を前景にして、面積 minArea 未満の粒（4近傍でつながった塊）を除く。
     * ピクセル数が多いので型を固定して高速化している。
     */
    @CompileStatic
    static byte[] buildMask(ImageProcessor ip, double thr, int minArea) {
        int w = ip.getWidth()
        int h = ip.getHeight()
        int n = w * h
        boolean[] fg = new boolean[n]
        for (int i = 0; i < n; i++) fg[i] = ip.getf(i) >= thr
        byte[] out = new byte[n]
        boolean[] seen = new boolean[n]
        int[] queue = new int[n]
        for (int s = 0; s < n; s++) {
            if (!fg[s] || seen[s]) continue
            int head = 0
            int tail = 0
            queue[tail++] = s
            seen[s] = true
            while (head < tail) {
                int p = queue[head++]
                int x = p % w
                int y = Math.floorDiv(p, w)
                if (x > 0 && fg[p - 1] && !seen[p - 1]) { seen[p - 1] = true; queue[tail++] = p - 1 }
                if (x < w - 1 && fg[p + 1] && !seen[p + 1]) { seen[p + 1] = true; queue[tail++] = p + 1 }
                if (y > 0 && fg[p - w] && !seen[p - w]) { seen[p - w] = true; queue[tail++] = p - w }
                if (y < h - 1 && fg[p + w] && !seen[p + w]) { seen[p + w] = true; queue[tail++] = p + w }
            }
            if (tail >= minArea) {
                for (int k = 0; k < tail; k++) out[queue[k]] = (byte) 1
            }
        }
        return out
    }

    /** プレビュー用: マスクを半透明の黄色で重ねる */
    void updateMaskPreview(int z, int t) {
        if (!adUseMask || !adPreview) { previewMaskRoi = null; return }
        def mask = currentMask(z, t)
        if (previewMaskRoi != null) return
        int w = imp.getWidth(), h = imp.getHeight()
        def cp = new ColorProcessor(w, h)
        int[] px = (int[]) cp.getPixels()
        fillMaskPixels(mask, px, 0xffff00)
        def roi = new ImageRoi(0, 0, cp)
        roi.setZeroTransparent(true)
        roi.setOpacity(0.35)
        roi.setName(MARKER_TAG + "-preview-mask")
        if (imp.isHyperStack()) roi.setPosition(0, z, t)
        else if (imp.getStackSize() > 1) roi.setPosition(imp.getNSlices() > 1 ? z : t)
        previewMaskRoi = roi
    }

    @CompileStatic
    static void fillMaskPixels(byte[] mask, int[] px, int rgb) {
        for (int i = 0; i < mask.length; i++) if (mask[i] != (byte) 0) px[i] = rgb
    }

    /**
     * 今の設定で細胞を検出する。
     * wait = false のとき（プレビュー）、StarDist がまだ計算中なら null を返す（裏で計算を始める）
     */
    def detectCells(boolean wait = true) {
        return isStarDist() ? detectStarDist(wait) : detectMaxima()
    }

    def detectMaxima() {
        int z = imp.getZ(), t = imp.getT()
        def ip = blurredChannel(adChannel, adSigma, z, t)
        def poly = new MaximumFinder().getMaxima(ip, adProm, true)
        byte[] mask = adUseMask ? currentMask(z, t) : null
        int w = imp.getWidth()
        def area = countingArea()
        def fip = adFilterCh > 0 ? imp.getStack().getProcessor(imp.getStackIndex(adFilterCh, z, t)) : null
        def res = []
        for (int i = 0; i < poly.npoints; i++) {
            int x = poly.xpoints[i], y = poly.ypoints[i]
            if (area != null && !area.contains(x, y)) continue
            if (mask != null && mask[y * w + x] == (byte) 0) continue
            if (adMinInt > 0 && ip.getf(x, y) < adMinInt) continue
            if (fip != null && localMean(fip, x, y, adFilterRadius) < adFilterThr) continue
            res << new EccMarker(x: x + 0.5, y: y + 0.5, type: adType, z: z, t: t)
        }
        return res
    }


    // ============================================================ StarDist（AI による核の検出）
    /*
     * Fiji の StarDist プラグイン（更新サイト CSBDeep + StarDist）を呼び出して、
     * 核を1個ずつ切り出したラベル画像を作る。各核について
     *   面積 → 範囲外（小さい点・大きすぎる塊）は捨てる
     *   重心 → マーカーの位置
     *   陽性チャンネルの「核の領域内の平均輝度」→ 陽性判定
     * StarDist が入っていなくてもこのスクリプト自体は動くよう、クラスは名前で呼び出す。
     */
    static final String[] DETECT_METHODS = ["StarDist（AI）", "Find Maxima（従来）"] as String[]
    static final String SD_COMMAND = "de.csbdresden.stardist.StarDist2D"
    static final String SD_MODEL = "Versatile (fluorescent nuclei)"

    int adMethod = (int) Prefs.get(AD + "method", 0)            // 0 = StarDist, 1 = Find Maxima
    double adSdProb = Prefs.get(AD + "sdProb", 0.5)
    double adSdNms = Prefs.get(AD + "sdNms", 0.4)
    int adSdMinArea = (int) Prefs.get(AD + "sdMinArea", 50)
    int adSdMaxArea = (int) Prefs.get(AD + "sdMaxArea", 0)      // 0 = 上限なし

    // 計算結果のキャッシュ: [key, ラベル画像]（裏のスレッドからも書くので丸ごと差し替える）
    volatile java.util.List sdCache = null
    volatile Map sdWanted = null
    volatile boolean sdRunning = false
    volatile String sdError = null
    volatile String sdErrorKey = null
    def sdDialogLabel = null          // プレビュー中のダイアログの「検出数」ラベル
    boolean[] sdKeep = null           // プレビュー用: 採用した核の番号

    boolean isStarDist() { return adMethod == 0 }

    String sdKeyOf(int ch, int z, int t, double prob, double nms) {
        return "${ch},${z},${t},${prob},${nms}".toString()
    }

    /** StarDist を1回実行してラベル画像（0 = 背景、1,2,3… = 核の番号）を返す */
    ImageProcessor runStarDist(int ch, int z, int t, double prob, double nms) {
        def ctx = (Context) IJ.runPlugIn("org.scijava.Context", "")
        def cmd = ctx.getService(CommandService)
        def dss = ctx.getService(DatasetService)
        if (cmd.getCommand(SD_COMMAND) == null) {
            throw new IllegalStateException("STARDIST_MISSING")
        }
        // 検出チャンネルの今の Z・T の1枚だけを渡す
        def plane = imp.getStack().getProcessor(imp.getStackIndex(ch, z, t)).duplicate()
        def single = new ImagePlus("ecc-stardist-input", plane)
        def ds = dss.create(new ImgPlus(ImageJFunctions.wrap(single)))

        def inputs = [
            input              : ds,
            modelChoice        : SD_MODEL,
            normalizeInput     : true,
            percentileBottom   : 1.0d,
            percentileTop      : 99.8d,
            probThresh         : prob,
            nmsThresh          : nms,
            outputType         : "Label Image",
            nTiles             : 1,
            excludeBoundary    : 2,
            roiPosition        : "Automatic",
            verbose            : false,
            showCsbdeepProgress: false,
            showProbAndDist    : false
        ]
        def future = cmd.run(SD_COMMAND, false, inputs)
        if (future == null) throw new IllegalStateException("STARDIST_MISSING")
        def module = future.get()
        def label = module.getOutput("label")
        if (label == null) throw new IllegalStateException("StarDist の結果（ラベル画像）が受け取れませんでした")
        def labImp = ImageJFunctions.wrap(label.getImgPlus(), "ecc-stardist-labels")
        return labImp.getProcessor().duplicate().convertToFloat()
    }

    /** キャッシュがあれば返す。なければ wait=true で今すぐ計算、wait=false なら裏で計算して null */
    ImageProcessor stardistLabels(int z, int t, boolean wait) {
        def key = sdKeyOf(adChannel, z, t, adSdProb, adSdNms)
        def c = sdCache
        if (c != null && c[0] == key) return (ImageProcessor) c[1]
        if (wait) {
            IJ.showStatus("StarDist で核を検出しています…")
            def lab = runStarDist(adChannel, z, t, adSdProb, adSdNms)
            sdCache = [key, lab]
            return lab
        }
        if (sdError != null && sdErrorKey == key) return null   // 同じ設定で失敗済み
        requestStarDist([key: key, ch: adChannel, z: z, t: t, prob: adSdProb, nms: adSdNms])
        return null
    }

    /** 裏のスレッドで StarDist を回す。計算中に設定が変わったら、終わってから最新の設定でもう一度 */
    void requestStarDist(Map req) {
        sdWanted = req
        if (sdRunning) return
        sdRunning = true
        sdError = null
        sdErrorKey = null
        Thread.start {
            def r = null
            try {
                while (true) {
                    r = sdWanted
                    def lab = runStarDist(r.ch as int, r.z as int, r.t as int, r.prob as double, r.nms as double)
                    sdCache = [r.key, lab]
                    if (sdWanted.is(r)) break
                }
            } catch (Throwable ex) {
                sdError = starDistErrorText(ex)
                sdErrorKey = r?.key
            } finally {
                sdRunning = false
                SwingUtilities.invokeLater { if (sdDialogLabel != null) refreshPreview(sdDialogLabel) }
            }
        }
    }

    static String starDistErrorText(Throwable ex) {
        def root = ex
        while (root.getCause() != null) root = root.getCause()
        if ((ex.getMessage() ?: "").contains("STARDIST_MISSING") || (root.getMessage() ?: "").contains("STARDIST_MISSING")) {
            return "StarDist が入っていません"
        }
        return "StarDist でエラー: " + (root.getMessage() ?: root.getClass().getSimpleName())
    }

    static final String STARDIST_INSTALL_HELP =
        "StarDist が Fiji に入っていないみたい。\n\n" +
        "1. Help › Update… を開く\n" +
        "2. Manage update sites を押す\n" +
        "3. 「CSBDeep」と「StarDist」にチェックを入れて Apply and close\n" +
        "4. Apply changes を押して、Fiji を再起動\n\n" +
        "入れるまでは、検出方法を「Find Maxima（従来）」にすれば今まで通り使えます。"

    def detectStarDist(boolean wait) {
        int z = imp.getZ(), t = imp.getT()
        def lab = stardistLabels(z, t, wait)
        if (lab == null) return null
        def fip = adFilterCh > 0 ? imp.getStack().getProcessor(imp.getStackIndex(adFilterCh, z, t)) : null
        double[][] st = labelStats(lab, fip)
        def area = countingArea()
        // 1) 面積と範囲で「核」として採用するものを決める
        def cand = []   // [番号, cx, cy, 核内平均]
        for (int v = 1; v < st[0].length; v++) {
            double n = st[0][v]
            if (n <= 0) continue
            if (n < adSdMinArea) continue                         // 小さい点（パンクタ）を捨てる
            if (adSdMaxArea > 0 && n > adSdMaxArea) continue      // 大きすぎる塊を捨てる
            double cx = st[1][v] / n + 0.5, cy = st[2][v] / n + 0.5
            if (area != null && !area.contains((int) cx, (int) cy)) continue
            cand << [v, cx, cy, st[3][v] / n]
        }
        sdTotalNuclei = cand.size()

        // 2) 陽性判定（核の領域内の平均輝度）
        sdThrUsed = Double.NaN
        sdMedian = Double.NaN
        if (fip != null && !cand.isEmpty()) {
            double[] means = cand.collect { it[3] as double } as double[]
            double[] sorted = means.clone()
            Arrays.sort(sorted)
            sdMedian = sorted[(int) (sorted.length / 2)]
            sdThrUsed = adFilterAuto ? otsuThreshold(means) : adFilterThr
        }

        boolean[] keep = new boolean[st[0].length]
        def res = []
        cand.each { c ->
            if (fip != null && (c[3] as double) < sdThrUsed) return
            keep[c[0] as int] = true
            res << new EccMarker(x: c[1] as double, y: c[2] as double, type: adType, z: z, t: t)
        }
        sdKeep = keep
        return res
    }

    int sdTotalNuclei = 0
    double sdThrUsed = Double.NaN
    double sdMedian = Double.NaN

    /** 大津法: 値の集まりを2群に分けるしきい値（これ以上が陽性） */
    @CompileStatic
    static double otsuThreshold(double[] v) {
        if (v.length == 0) return 0
        double mn = v[0], mx = v[0]
        for (double x : v) { if (x < mn) mn = x; if (x > mx) mx = x }
        if (mx <= mn) return mn
        int nb = 256
        double step = (mx - mn) / (nb - 1)
        int[] hist = new int[nb]
        for (double x : v) hist[(int) ((x - mn) / step)]++
        double total = v.length
        double sumAll = 0
        for (int i = 0; i < nb; i++) sumAll += i * hist[i]
        double wB = 0, sumB = 0, best = -1
        int bestT = 0
        for (int t = 0; t < nb; t++) {
            wB += hist[t]
            if (wB == 0) continue
            double wF = total - wB
            if (wF == 0) break
            sumB += t * hist[t]
            double mB = sumB / wB
            double mF = (sumAll - sumB) / wF
            double between = wB * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; bestT = t }
        }
        return mn + (bestT + 1) * step
    }

    /** ラベルごとの [画素数, x の合計, y の合計, 陽性チャンネル輝度の合計] */
    @CompileStatic
    static double[][] labelStats(ImageProcessor lab, ImageProcessor pos) {
        int w = lab.getWidth()
        int h = lab.getHeight()
        int maxL = 0
        for (int i = 0; i < w * h; i++) {
            int v = (int) lab.getf(i)
            if (v > maxL) maxL = v
        }
        double[] n = new double[maxL + 1]
        double[] sx = new double[maxL + 1]
        double[] sy = new double[maxL + 1]
        double[] sp = new double[maxL + 1]
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x
                int v = (int) lab.getf(i)
                if (v <= 0) continue
                n[v] += 1
                sx[v] += x
                sy[v] += y
                if (pos != null) sp[v] += pos.getf(i)
            }
        }
        double[][] r = new double[4][]
        r[0] = n
        r[1] = sx
        r[2] = sy
        r[3] = sp
        return r
    }

    /** 採用した核の輪郭だけを px に描く */
    @CompileStatic
    static void fillOutlines(ImageProcessor lab, boolean[] keep, int[] px, int rgb) {
        int w = lab.getWidth()
        int h = lab.getHeight()
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x
                int v = (int) lab.getf(i)
                if (v <= 0 || v >= keep.length || !keep[v]) continue
                boolean edge = x == 0 || y == 0 || x == w - 1 || y == h - 1 ||
                    (int) lab.getf(i - 1) != v || (int) lab.getf(i + 1) != v ||
                    (int) lab.getf(i - w) != v || (int) lab.getf(i + w) != v
                if (edge) px[i] = rgb
            }
        }
    }

    /** プレビュー用: StarDist で切り出した核の輪郭を重ねる */
    void updateOutlinePreview(int z, int t) {
        def c = sdCache
        if (!adPreview || c == null || sdKeep == null) { previewMaskRoi = null; return }
        def lab = (ImageProcessor) c[1]
        def cp = new ColorProcessor(lab.getWidth(), lab.getHeight())
        fillOutlines(lab, sdKeep, (int[]) cp.getPixels(), TYPE_COLORS[adType].getRGB() & 0xffffff)
        def roi = new ImageRoi(0, 0, cp)
        roi.setZeroTransparent(true)
        roi.setOpacity(0.9)
        roi.setName(MARKER_TAG + "-preview-mask")
        if (imp.isHyperStack()) roi.setPosition(0, z, t)
        else if (imp.getStackSize() > 1) roi.setPosition(imp.getNSlices() > 1 ? z : t)
        previewMaskRoi = roi
    }

    void autoDetect() {
        int nc = imp.getNChannels()
        adChannel = Math.max(1, Math.min(adChannel, nc))
        if (adFilterCh > nc) adFilterCh = 0
        adType = selectedType()

        String[] chs = (1..nc).collect { "Channel " + it } as String[]
        String[] fchs = (["なし"] + (1..nc).collect { "Channel " + it }) as String[]
        String[] types = (0..<N_TYPES).collect { "${it + 1}: ${typeName(it)}".toString() } as String[]

        adMethod = Math.max(0, Math.min(adMethod, DETECT_METHODS.length - 1))
        def gd = new GenericDialog("Auto Detect（半自動カウント）")
        gd.addChoice("検出に使うチャンネル", chs, chs[adChannel - 1])
        gd.addChoice("追加先のタイプ", types, types[adType])
        gd.addChoice("検出方法", DETECT_METHODS, DETECT_METHODS[adMethod])
        gd.addMessage("―― StarDist（AI）のとき ――")
        gd.addNumericField("確率しきい値（0〜1、大きいほど厳しい）", adSdProb, 2)
        gd.addNumericField("重なりしきい値 NMS（0〜1）", adSdNms, 2)
        gd.addNumericField("最小面積 px（小さい点を除く）", adSdMinArea, 0)
        gd.addNumericField("最大面積 px（0 で上限なし）", adSdMaxArea, 0)
        gd.addMessage("―― Find Maxima（従来）のとき ――")
        gd.addNumericField("ぼかし σ (px)", adSigma, 1)
        gd.addNumericField("Prominence（大きいほど厳しい）", adProm, 1)
        gd.addNumericField("最低輝度（0 で無効）", adMinInt, 1)
        gd.addMessage("―― 背景マスク（二値化）――")
        gd.addCheckbox("二値化した前景の中だけ数える（背景ノイズ対策）", adUseMask)
        String method0 = (MASK_METHODS as java.util.List).contains(adMaskMethod) ? adMaskMethod : "Li"
        gd.addChoice("しきい値の決め方", MASK_METHODS, method0)
        gd.addNumericField("手動しきい値（「手動」のとき）", adMaskThr, 1)
        gd.addNumericField("小さい粒を除く（最小面積 px）", adMaskMinArea, 0)
        gd.addMessage("―― 陽性で絞り込む（任意）――")
        gd.addChoice("陽性を見るチャンネル", fchs, fchs[adFilterCh])
        gd.addNumericField("しきい値（周りの平均輝度）", adFilterThr, 1)
        gd.addCheckbox("しきい値を自動で決める（大津法・StarDist のとき）", adFilterAuto)
        gd.addNumericField("見る範囲の半径 (px)（Find Maxima のときだけ）", adFilterRadius, 0)
        gd.addMessage("※ StarDist のときは、切り出した核の領域の中の平均輝度で判定します")
        gd.addMessage("――――――――――――――――")
        gd.addCheckbox("このタイプの既存マーカーを置き換える（今のスライス・範囲内）", adReplace)
        gd.addCheckbox("プレビュー", adPreview)
        gd.addMessage("検出数: ―                                                                                    ")
        def countLabel = gd.getMessage()
        def sel = imp.getRoi()
        gd.addMessage((sel != null && sel.isArea()) ? "囲んだ範囲の中だけ数えます" :
            (!regions.isEmpty() ? "登録したリージョン（${regions.size()} 個）の中だけ数えます".toString() :
            "ヒント: 範囲ツールで囲むか、リージョンを登録すると、その中だけ数えます"))

        gd.addDialogListener({ GenericDialog g, AWTEvent e ->
            adChannel = g.getNextChoiceIndex() + 1
            adType = g.getNextChoiceIndex()
            adMethod = g.getNextChoiceIndex()
            adSdProb = Math.max(0, Math.min(1, g.getNextNumber()))
            adSdNms = Math.max(0, Math.min(1, g.getNextNumber()))
            adSdMinArea = (int) Math.max(0, g.getNextNumber())
            adSdMaxArea = (int) Math.max(0, g.getNextNumber())
            adSigma = Math.max(0, g.getNextNumber())
            adProm = Math.max(0, g.getNextNumber())
            adMinInt = g.getNextNumber()
            adUseMask = g.getNextBoolean()
            adMaskMethod = g.getNextChoice()
            adMaskThr = g.getNextNumber()
            adMaskMinArea = (int) Math.max(0, g.getNextNumber())
            adFilterCh = g.getNextChoiceIndex()
            adFilterThr = g.getNextNumber()
            adFilterRadius = (int) Math.max(0, g.getNextNumber())
            adFilterAuto = g.getNextBoolean()
            adReplace = g.getNextBoolean()
            adPreview = g.getNextBoolean()
            if (g.invalidNumber()) return false
            refreshPreview(countLabel)
            return true
        } as DialogListener)

        // 開いた直後にも一度プレビュー
        sdDialogLabel = countLabel
        refreshPreview(countLabel)

        gd.showDialog()
        sdDialogLabel = null

        if (gd.wasOKed()) {
            def found
            try {
                found = detectCells(true)
            } catch (Throwable ex) {
                def msg = starDistErrorText(ex)
                JOptionPane.showMessageDialog(frame, msg == "StarDist が入っていません" ? STARDIST_INSTALL_HELP : msg)
                found = null
            }
            if (found == null) {
                previewMarkers = []
                previewMaskRoi = null
                redrawMarkers()
                return
            }
            pushUndo()
            if (adReplace) {
                def area = countingArea()
                int z = imp.getZ(), t = imp.getT()
                markers.removeAll { EccMarker m ->
                    m.type == adType && m.z == z && m.t == t &&
                        (area == null || area.contains((int) m.x, (int) m.y))
                }
            }
            markers.addAll(found)
            radios[adType].setSelected(true)
            visibleBoxes[adType].setSelected(true)   // 追加した結果が見えるように
            saveAutoPrefs()
            IJ.showStatus("${typeName(adType)} に ${found.size()} 個追加しました（Undo で取り消せます）".toString())
        }
        previewMarkers = []
        previewMaskRoi = null
        blurred = null
        blurKey = null
        maskData = null
        maskKey = null
        redrawMarkers()
    }

    void refreshPreview(countLabel) {   // Label / MultiLineLabel どちらでも
        if (adPreview && isStarDist()) {
            def found = detectCells(false)
            if (found == null) {
                // 計算中、またはエラー
                if (sdError != null && !sdRunning) {
                    previewMarkers = []
                    previewMaskRoi = null
                    countLabel?.setText(sdError == "StarDist が入っていません" ?
                        "StarDist が入っていません（OK を押すと入れ方を表示）" : sdError)
                } else {
                    countLabel?.setText("StarDist で計算中…（数秒かかります）")
                }
            } else {
                previewMarkers = found
                updateOutlinePreview(imp.getZ(), imp.getT())
                def txt = "検出数: ${found.size()} 個（StarDist）"
                if (adFilterCh > 0) {
                    txt = "陽性: ${found.size()} / 全核 ${sdTotalNuclei} 個　" +
                          "核内平均の中央値 ${fmt(sdMedian)}・しきい値 ${fmt(sdThrUsed)}" +
                          (adFilterAuto ? "（自動）" : "")
                }
                countLabel?.setText(txt.toString())
            }
        } else if (adPreview) {
            previewMarkers = detectCells()
            previewMaskRoi = null
            updateMaskPreview(imp.getZ(), imp.getT())
            def txt = "検出数: ${previewMarkers.size()} 個"
            if (adUseMask && !Double.isNaN(maskThrUsed)) txt += "（二値化しきい値 ${fmt(maskThrUsed)}）"
            countLabel?.setText(txt.toString())
        } else {
            previewMarkers = []
            previewMaskRoi = null
            countLabel?.setText("検出数: ―（プレビュー OFF）")
        }
        redrawMarkers()
    }

    void saveAutoPrefs() {
        Prefs.set(AD + "method", adMethod)
        Prefs.set(AD + "sdProb", adSdProb)
        Prefs.set(AD + "sdNms", adSdNms)
        Prefs.set(AD + "sdMinArea", adSdMinArea)
        Prefs.set(AD + "sdMaxArea", adSdMaxArea)
        Prefs.set(AD + "useMask", adUseMask)
        Prefs.set(AD + "maskMethod", adMaskMethod)
        Prefs.set(AD + "maskThreshold", adMaskThr)
        Prefs.set(AD + "maskMinArea", adMaskMinArea)
        Prefs.set(AD + "channel", adChannel)
        Prefs.set(AD + "sigma", adSigma)
        Prefs.set(AD + "prominence", adProm)
        Prefs.set(AD + "minIntensity", adMinInt)
        Prefs.set(AD + "filterChannel", adFilterCh)
        Prefs.set(AD + "filterThreshold", adFilterThr)
        Prefs.set(AD + "filterRadius", adFilterRadius)
        Prefs.set(AD + "filterAuto", adFilterAuto)
        Prefs.set(AD + "replace", adReplace)
    }

    // ============================================================ 保存・読み込み
    String baseName() {
        return file.getName().replaceFirst(/\.[^.]+$/, "")
    }

    /** 保存先を選ばせる（拡張子補完・上書き確認つき）。キャンセル時は null */
    File chooseSaveFile(String title, String defaultName, String defaultExt) {
        def fc = new JFileChooser(file.getParentFile())
        fc.setDialogTitle(title)
        fc.setSelectedFile(new File(file.getParentFile(), defaultName))
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return null
        File out = fc.getSelectedFile()
        if (!out.getName().contains(".")) {
            out = new File(out.getParentFile(), out.getName() + defaultExt)
        }
        if (out.exists()) {
            int ans = JOptionPane.showConfirmDialog(frame,
                "${out.getName()} はもうあります。上書きしますか？".toString(),
                "上書きの確認", JOptionPane.YES_NO_OPTION)
            if (ans != JOptionPane.YES_OPTION) return null
        }
        return out
    }

    static void writeCsv(File out, java.util.List rows) {
        def w = new OutputStreamWriter(new FileOutputStream(out), "UTF-8")
        try {
            w.write("\uFEFF")
            rows.each { r -> w.write(r.collect { csvCell(it as String) }.join(",") + "\r\n") }
        } finally {
            w.close()
        }
    }

    /** ダブルクォート対応の簡易CSVリーダー（BOMも除去） */
    static java.util.List<java.util.List<String>> readCsv(File f) {
        def text = f.getText("UTF-8")
        if (text.startsWith("\uFEFF")) text = text.substring(1)
        def rows = []
        def row = []
        def cell = new StringBuilder()
        boolean inQuote = false
        int i = 0
        while (i < text.length()) {
            char ch = text.charAt(i)
            if (inQuote) {
                if (ch == '"' as char) {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"' as char) { cell.append('"'); i++ }
                    else inQuote = false
                } else cell.append(ch)
            } else if (ch == '"' as char) {
                inQuote = true
            } else if (ch == ',' as char) {
                row << cell.toString(); cell.setLength(0)
            } else if (ch == '\n' as char || ch == '\r' as char) {
                if (ch == '\r' as char && i + 1 < text.length() && text.charAt(i + 1) == '\n' as char) i++
                row << cell.toString(); cell.setLength(0)
                rows << row; row = []
            } else cell.append(ch)
            i++
        }
        if (cell.length() > 0 || !row.isEmpty()) { row << cell.toString(); rows << row }
        return rows
    }

    /**
     * 形式を選べる保存ダイアログ。formats は [[説明, ".拡張子"], ...]（先頭がデフォルト）。
     * 形式を切り替えるとファイル名の拡張子も追従する。キャンセル時は null。
     */
    File chooseSaveFileWithFormats(String title, String base, java.util.List formats) {
        def fc = new JFileChooser(file.getParentFile())
        fc.setDialogTitle(title)
        fc.setAcceptAllFileFilterUsed(false)
        def filters = formats.collect { f ->
            new FileNameExtensionFilter("${f[0]} (*${f[1]})".toString(), (f[1] as String).substring(1))
        }
        filters.each { fc.addChoosableFileFilter(it) }
        fc.setFileFilter(filters[0])
        fc.setSelectedFile(new File(file.getParentFile(), base + formats[0][1]))

        fc.addPropertyChangeListener(JFileChooser.FILE_FILTER_CHANGED_PROPERTY, { PropertyChangeEvent e ->
            int idx = filters.indexOf(fc.getFileFilter())
            if (idx < 0) return
            String name = chooserFileName(fc) ?: base
            name = name.replaceFirst(/\.[^.]+$/, "") + formats[idx][1]
            SwingUtilities.invokeLater { fc.setSelectedFile(new File(fc.getCurrentDirectory(), name)) }
        } as PropertyChangeListener)

        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return null
        File out = fc.getSelectedFile()
        int idx = Math.max(0, filters.indexOf(fc.getFileFilter()))
        def lower = out.getName().toLowerCase()
        def known = formats.collect { it[1] as String } + [".jpeg", ".tiff"]
        // 拡張子を手で打っていればそれを優先、なければ選んだ形式の拡張子を付ける
        if (!known.any { lower.endsWith(it) }) {
            out = new File(out.getParentFile(), out.getName() + formats[idx][1])
        }
        if (out.exists()) {
            int ans = JOptionPane.showConfirmDialog(frame,
                "${out.getName()} はもうあります。上書きしますか？".toString(),
                "上書きの確認", JOptionPane.YES_NO_OPTION)
            if (ans != JOptionPane.YES_OPTION) return null
        }
        return out
    }

    /** ファイル名欄に今入力されている文字列（取れなければ選択中ファイル名） */
    static String chooserFileName(JFileChooser fc) {
        def ui = fc.getUI()
        if (ui instanceof BasicFileChooserUI) {
            def n = ((BasicFileChooserUI) ui).getFileName()
            if (n != null && !n.trim().isEmpty()) return n.trim()
        }
        return fc.getSelectedFile()?.getName()
    }

    // ------------------------------------------------------------ マーカー保存
    /**
     * マーカーの保存。形式は2つ:
     *  - XML: ImageJ 標準 Cell Counter と同じ形式（標準の Cell Counter でも開ける）
     *         座標は整数、MarkerZ はスタック上の通し番号
     *  - CSV: 1マーカー1行（x, y, z, t, type, type_name）。小数座標のまま。解析向け
     */
    void saveMarkers() {
        if (markers.isEmpty()) { JOptionPane.showMessageDialog(frame, "マーカーがまだありません"); return }
        File out = chooseSaveFileWithFormats("マーカーを保存", baseName() + "_markers", [
            ["Cell Counter XML", ".xml"],
            ["CSV", ".csv"]
        ])
        if (out == null) return
        try {
            if (out.getName().toLowerCase().endsWith(".csv")) writeMarkersCsv(out)
            else writeMarkersXml(out)
            IJ.showStatus("マーカーを保存しました: " + out.getName())
            JOptionPane.showMessageDialog(frame,
                "${markers.size()} 個のマーカーを保存しました\n${out.getAbsolutePath()}".toString())
        } catch (Exception ex) {
            IJ.handleException(ex)
        }
    }

    void writeMarkersCsv(File out) {
        def rows = [["x", "y", "z", "t", "type", "type_name"]]
        markers.each { EccMarker m ->
            rows << [String.format("%.2f", m.x), String.format("%.2f", m.y),
                     String.valueOf(m.z), String.valueOf(m.t),
                     String.valueOf(m.type + 1), typeName(m.type)]
        }
        writeCsv(out, rows)
    }

    static String xmlEsc(String s) {
        return (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        .replace("\"", "&quot;").replace("'", "&apos;")
    }

    void writeMarkersXml(File out) {
        def sb = new StringBuilder()
        sb << '<?xml version="1.0" encoding="UTF-8"?>\n'
        sb << '<CellCounter_Marker_File>\n'
        sb << '  <Image_Properties>\n'
        sb << '    <Image_Filename>' << xmlEsc(file.getName()) << '</Image_Filename>\n'
        sb << '  </Image_Properties>\n'
        sb << '  <Marker_Data>\n'
        sb << '    <Current_Type>' << (selectedType() + 1) << '</Current_Type>\n'
        for (int i = 0; i < N_TYPES; i++) {
            sb << '    <Marker_Type>\n'
            sb << '      <Type>' << (i + 1) << '</Type>\n'
            sb << '      <Name>' << xmlEsc(typeName(i)) << '</Name>\n'
            markers.findAll { it.type == i }.each { EccMarker m ->
                sb << '      <Marker>\n'
                sb << '        <MarkerX>' << Math.round(m.x) << '</MarkerX>\n'
                sb << '        <MarkerY>' << Math.round(m.y) << '</MarkerY>\n'
                sb << '        <MarkerZ>' << imp.getStackIndex(1, m.z, m.t) << '</MarkerZ>\n'
                sb << '      </Marker>\n'
            }
            sb << '    </Marker_Type>\n'
        }
        sb << '  </Marker_Data>\n'
        sb << '</CellCounter_Marker_File>\n'
        out.setText(sb.toString(), "UTF-8")
    }

    // ------------------------------------------------------------ マーカー読み込み
    void loadMarkers() {
        def fc = new JFileChooser(file.getParentFile())
        fc.setDialogTitle("マーカーを読み込み")
        fc.setFileFilter(new FileNameExtensionFilter("マーカー (*.xml, *.csv)", "xml", "csv"))
        for (String ext : [".xml", ".csv"]) {
            def guess = new File(file.getParentFile(), baseName() + "_markers" + ext)
            if (guess.exists()) { fc.setSelectedFile(guess); break }
        }
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        File f = fc.getSelectedFile()

        def result   // [loaded markers, names map, skipped count]
        try {
            result = f.getName().toLowerCase().endsWith(".xml") ? readMarkersXml(f) : readMarkersCsv(f)
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "読み込めませんでした:\n" + ex.getMessage())
            return
        }
        if (result == null) return
        def loaded = result[0]
        def names = result[1]
        int skipped = result[2] as int

        // 既にマーカーがあれば、置き換えるか追加するか聞く
        boolean replace = true
        if (!markers.isEmpty()) {
            Object[] opts = ["置き換える", "追加する", "キャンセル"] as Object[]
            int ans = JOptionPane.showOptionDialog(frame,
                "今あるマーカー（${markers.size()} 個）はどうする？".toString(), "Load Markers",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opts, opts[0])
            if (ans == 2 || ans < 0) return
            replace = (ans == 0)
        }

        pushUndo()
        if (replace) markers.clear()
        markers.addAll(loaded)
        names.each { k, v -> nameFields[k as int].setText(v as String) }   // 保存時のタイプ名を復元
        redrawMarkers()

        def msg = "${loaded.size()} 個のマーカーを読み込みました"
        if (skipped > 0) msg += "\n（範囲外・読めない点を ${skipped} 個スキップ）"
        JOptionPane.showMessageDialog(frame, msg.toString())
    }

    /** 画像に収まる点か */
    boolean inBounds(double x, double y, int z, int t, int type) {
        return type >= 0 && type < N_TYPES &&
            x >= 0 && y >= 0 && x < imp.getWidth() && y < imp.getHeight() &&
            z >= 1 && z <= imp.getNSlices() && t >= 1 && t <= imp.getNFrames()
    }

    /** 今の画像が1枚もの（Z・Tが1）か。最大値投影した画像など */
    boolean singlePlane() {
        return imp.getNSlices() * imp.getNFrames() == 1
    }

    def readMarkersCsv(File f) {
        def rows = readCsv(f)
        if (rows.isEmpty()) throw new IllegalArgumentException("中身が空のファイルです")
        def header = rows[0].collect { it.trim().toLowerCase() }
        int ix = header.indexOf("x"), iy = header.indexOf("y")
        int iz = header.indexOf("z"), it_ = header.indexOf("t")
        int itype = header.indexOf("type"), iname = header.indexOf("type_name")
        if (ix < 0 || iy < 0 || itype < 0) {
            throw new IllegalArgumentException("x, y, type の列が見つかりません。このツールで保存したマーカーCSVを選んでね。")
        }
        def loaded = []
        def names = [:]
        int skipped = 0
        rows.drop(1).each { r ->
            if (r.size() == 1 && r[0].trim().isEmpty()) return   // 空行
            try {
                double x = r[ix] as double, y = r[iy] as double
                int z = (iz >= 0 && iz < r.size()) ? (r[iz] as int) : 1
                int t = (it_ >= 0 && it_ < r.size()) ? (r[it_] as int) : 1
                if (singlePlane()) { z = 1; t = 1 }
                int type = (r[itype] as int) - 1
                if (!inBounds(x, y, z, t, type)) { skipped++; return }
                loaded << new EccMarker(x: x, y: y, z: z, t: t, type: type)
                if (iname >= 0 && iname < r.size() && !r[iname].trim().isEmpty()) names[type] = r[iname].trim()
            } catch (Exception ignore) {
                skipped++
            }
        }
        return [loaded, names, skipped]
    }

    /** 標準 Cell Counter の XML（このツールで保存したものも含む）を読む */
    def readMarkersXml(File f) {
        def doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        def types = doc.getElementsByTagName("Marker_Type")
        def loaded = []
        def names = [:]
        int skipped = 0
        for (int i = 0; i < types.getLength(); i++) {
            def mt = (Element) types.item(i)
            def typeText = childText(mt, "Type")
            if (typeText == null) continue
            int type = (typeText as int) - 1
            def name = childText(mt, "Name")
            if (name != null && !name.trim().isEmpty() && type >= 0 && type < N_TYPES) names[type] = name.trim()

            def ms = mt.getElementsByTagName("Marker")
            for (int j = 0; j < ms.getLength(); j++) {
                def m = (Element) ms.item(j)
                try {
                    double x = childText(m, "MarkerX") as double
                    double y = childText(m, "MarkerY") as double
                    int z = 1, t = 1
                    def zt = childText(m, "MarkerZ")
                    if (!singlePlane() && zt != null) {
                        int idx = zt as int
                        if (idx < 1 || idx > imp.getStackSize()) { skipped++; continue }
                        int[] pos = imp.convertIndexToPosition(idx)   // [c, z, t]
                        z = pos[1]
                        t = pos[2]
                    }
                    if (!inBounds(x, y, z, t, type)) { skipped++; continue }
                    loaded << new EccMarker(x: x, y: y, z: z, t: t, type: type)
                } catch (Exception ignore) {
                    skipped++
                }
            }
        }
        if (types.getLength() == 0) throw new IllegalArgumentException("Cell Counter のマーカーXMLではないようです")
        return [loaded, names, skipped]
    }

    /** 直下の子要素 tag のテキスト（なければ null） */
    static String childText(Element el, String tag) {
        def nodes = el.getChildNodes()
        for (int i = 0; i < nodes.getLength(); i++) {
            def n = nodes.item(i)
            if (n instanceof Element && ((Element) n).getTagName() == tag) return n.getTextContent().trim()
        }
        return null
    }

    // ------------------------------------------------------------ 表示設定（B&C）
    static final String[] MODE_NAMES = ["Composite", "Color", "Grayscale"] as String[]

    /**
     * チャンネル c を一時的に現在チャンネルにして action を実行し、元のチャンネルに戻す。
     * 表示範囲の読み書きは B&C と同じく「現在チャンネル」経由で行う。
     */
    void eachChannel(Closure action) {
        int cur = imp.getC()
        boolean prev = syncing
        syncing = true
        try {
            for (int c = 1; c <= imp.getNChannels(); c++) {
                imp.setC(c)
                imp.updateAndDraw()
                action(c)
            }
        } finally {
            imp.setC(cur)
            imp.updateAndDraw()
            syncing = prev
        }
    }

    /**
     * 表示設定を JSON で保存:
     *   表示モード / チャンネルごとの表示範囲（min・max）/ チャンネルの ON・OFF
     */
    void saveDisplay() {
        File out = chooseSaveFile("B&C（表示設定）を保存", baseName() + "_display.json", ".json")
        if (out == null) return
        try {
            writeDisplayJson(out)
            IJ.showStatus("B&C を保存しました: " + out.getName())
            JOptionPane.showMessageDialog(frame, "保存しました\n" + out.getAbsolutePath())
        } catch (Exception ex) {
            IJ.handleException(ex)
        }
    }

    void writeDisplayJson(File out) {
        def chans = []
        eachChannel { int c ->
            chans << [channel: c, min: imp.getDisplayRangeMin(), max: imp.getDisplayRangeMax()]
        }
        String mode = "Single"
        if (imp.isComposite()) {
            def ci = (CompositeImage) imp
            mode = MODE_NAMES[Math.max(0, Math.min(2, ci.getMode() - 1))]
            boolean[] active = ci.getActiveChannels()
            chans.each { it.active = active[(it.channel as int) - 1] }
        }
        def data = [image: file.getName(), mode: mode, channels: chans]
        out.setText(JsonOutput.prettyPrint(JsonOutput.toJson(data)), "UTF-8")
    }

    void loadDisplay() {
        def fc = new JFileChooser(file.getParentFile())
        fc.setDialogTitle("B&C（表示設定）を読み込み")
        fc.setFileFilter(new FileNameExtensionFilter("表示設定 (*.json)", "json"))
        def guess = new File(file.getParentFile(), baseName() + "_display.json")
        if (guess.exists()) fc.setSelectedFile(guess)
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return

        def data
        try {
            data = new JsonSlurper().parse(fc.getSelectedFile(), "UTF-8")
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "読み込めませんでした:\n" + ex.getMessage())
            return
        }
        if (!(data instanceof Map) || !(data.channels instanceof java.util.List)) {
            JOptionPane.showMessageDialog(frame, "このツールで保存した表示設定ファイルではないようです")
            return
        }

        int n = imp.getNChannels()
        def byChannel = [:]
        data.channels.each { ch -> byChannel[ch.channel as int] = ch }

        // 表示モード
        if (imp.isComposite() && (MODE_NAMES as java.util.List).contains(data.mode)) {
            ((CompositeImage) imp).setMode(MODE_NAMES.findIndexOf { it == data.mode } + 1)
        }
        // チャンネルごとの表示範囲
        int applied = 0
        eachChannel { int c ->
            def ch = byChannel[c]
            if (ch != null && ch.min != null && ch.max != null) {
                imp.setDisplayRange(ch.min as double, ch.max as double)
                imp.updateAndDraw()
                applied++
            }
        }
        // チャンネルの ON / OFF
        if (imp.isComposite()) {
            boolean[] active = ((CompositeImage) imp).getActiveChannels()
            byChannel.each { c, ch ->
                if (ch.active != null && c >= 1 && c <= n) active[c - 1] = ch.active as boolean
            }
            imp.updateAndDraw()
        }
        refreshDisplayPanels()

        def msg = "${applied} チャンネル分の表示設定を読み込みました"
        if (data.channels.size() != n) {
            msg += "\n（ファイルは ${data.channels.size()} チャンネル、この画像は ${n} チャンネルです。番号が合うものだけ反映しました）"
        }
        JOptionPane.showMessageDialog(frame, msg.toString())
    }

    // ------------------------------------------------------------ 画像保存
    /** マーカーを焼き込んだ画像を保存（今表示しているZ・Tの1枚）。形式は保存ダイアログで選ぶ */
    String lastImageExt = ".png"   // Save All でも同じ形式を使う

    void saveImage() {
        def formats = [["PNG", ".png"], ["TIFF", ".tif"], ["JPEG", ".jpg"]]
        // 前回選んだ形式を先頭（デフォルト）にする
        formats.sort { it[1] == lastImageExt ? 0 : 1 }
        File out = chooseSaveFileWithFormats("マーカー・線入り画像を保存", baseName() + "_markers", formats)
        if (out == null) return
        if (writeFlatImage(out)) {
            def n = out.getName().toLowerCase()
            lastImageExt = n.endsWith(".tif") || n.endsWith(".tiff") ? ".tif" : (n.endsWith(".jpg") || n.endsWith(".jpeg") ? ".jpg" : ".png")
            IJ.showStatus("画像を保存しました: " + out.getName())
            JOptionPane.showMessageDialog(frame, "保存しました\n" + out.getAbsolutePath())
        }
    }

    /**
     * 今の見た目（チャンネル表示・B&C・マーカー・線）を1枚に焼き込んで保存。
     * 確定していない選択範囲（今引いたばかりの線など）も一緒に焼き込む。
     */
    boolean writeFlatImage(File out) {
        ImagePlus flat
        def sel = imp.getRoi()
        if (sel != null) {
            def orig = imp.getOverlay()
            def tmp = orig != null ? orig.duplicate() : new Overlay()
            tmp.add(styledCopy(sel))
            imp.setOverlay(tmp)
            try {
                flat = imp.flatten()
            } finally {
                imp.setOverlay(orig)
                imp.setRoi(sel)
            }
        } else {
            flat = imp.flatten()
        }
        def name = out.getName().toLowerCase()
        def saver = new FileSaver(flat)
        if (name.endsWith(".tif") || name.endsWith(".tiff")) return saver.saveAsTiff(out.getAbsolutePath())
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return saver.saveAsJpeg(out.getAbsolutePath())
        return saver.saveAsPng(out.getAbsolutePath())
    }

    // ------------------------------------------------------------ 線（アノテーション）
    /**
     * 「線」= ImageJ のツールで描いた選択範囲（直線・矢印・折れ線・四角・楕円・テキストなど）。
     * Add Line でオーバーレイに確定させると、いくつでも残せて、画像保存にも焼き込まれる。
     */
    static Roi styledCopy(Roi r) {
        def c = (Roi) r.clone()
        if (c.getStrokeColor() == null) c.setStrokeColor(Roi.getColor())
        if (c.getName() != null && c.getName().startsWith(MARKER_TAG)) c.setName(null)
        return c
    }

    /** オーバーレイ上の線（マーカー以外） */
    java.util.List<Roi> lineRois() {
        def ov = imp.getOverlay()
        if (ov == null) return []
        return ov.toArray().findAll { !isMarkerRoi(it) }
    }

    void addLine() {
        def sel = imp.getRoi()
        if (sel == null) {
            JOptionPane.showMessageDialog(frame,
                "先に ImageJ のツールバーで線や図形のツールを選んで、画像に描いてね。\n描いたあとにこのボタンで確定できます。")
            return
        }
        def ov = imp.getOverlay() ?: new Overlay()
        ov.add(styledCopy(sel))
        imp.setOverlay(ov)
        imp.deleteRoi()
        IJ.setTool("hand")   // カウントに戻れるようにハンドツールへ
        IJ.showStatus("線を確定しました（全 ${lineRois().size()} 本）".toString())
    }

    void removeLastLine() {
        def ov = imp.getOverlay()
        if (ov == null) return
        for (int i = ov.size() - 1; i >= 0; i--) {
            if (!isMarkerRoi(ov.get(i))) {
                ov.remove(i)
                imp.updateAndDraw()
                return
            }
        }
        IJ.showStatus("確定した線はありません")
    }

    void clearLines() {
        def n = lineRois().size()
        if (n == 0) return
        int ans = JOptionPane.showConfirmDialog(frame, "確定した線を全部消しますか？（${n} 本）".toString(),
            "Clear Lines", JOptionPane.YES_NO_OPTION)
        if (ans != JOptionPane.YES_OPTION) return
        def ov = imp.getOverlay()
        for (int i = ov.size() - 1; i >= 0; i--) {
            if (!isMarkerRoi(ov.get(i))) ov.remove(i)
        }
        imp.updateAndDraw()
    }

    /** 保存対象の線（確定済み ＋ まだ確定していない選択範囲） */
    java.util.List<Roi> linesToSave() {
        def list = lineRois().collect { (Roi) it.clone() }
        if (imp.getRoi() != null) list << styledCopy(imp.getRoi())
        return list
    }

    void saveLines() {
        def rois = linesToSave()
        if (rois.isEmpty()) { JOptionPane.showMessageDialog(frame, "保存する線がありません"); return }
        File out = chooseSaveFileWithFormats("線を保存", baseName() + "_lines", [
            ["XML", ".xml"],
            ["ImageJ ROIセット（ROI Manager で開ける）", ".zip"]
        ])
        if (out == null) return
        try {
            if (out.getName().toLowerCase().endsWith(".zip")) writeLinesZip(out, rois)
            else writeLinesXml(out, rois)
            IJ.showStatus("線を保存しました: " + out.getName())
            JOptionPane.showMessageDialog(frame, "${rois.size()} 本の線を保存しました\n${out.getAbsolutePath()}".toString())
        } catch (Exception ex) {
            IJ.handleException(ex)
        }
    }

    /** ImageJ の ROI Manager と同じ .zip 形式で保存（ROI Manager でも開ける） */
    static void writeLinesZip(File out, java.util.List<Roi> rois) {
        def zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))
        try {
            def re = new RoiEncoder(zos)
            rois.eachWithIndex { Roi r, int i ->
                zos.putNextEntry(new ZipEntry(String.format("line-%04d.roi", i + 1)))
                re.write(r)
                zos.flush()
            }
        } finally {
            zos.close()
        }
    }

    void loadLines() {
        def fc = new JFileChooser(file.getParentFile())
        fc.setDialogTitle("線を読み込み")
        fc.setFileFilter(new FileNameExtensionFilter("線 (*.xml, *.zip, *.roi)", "xml", "zip", "roi"))
        for (String ext : [".xml", ".zip"]) {
            def guess = new File(file.getParentFile(), baseName() + "_lines" + ext)
            if (guess.exists()) { fc.setSelectedFile(guess); break }
        }
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return
        File f = fc.getSelectedFile()

        def rois = []
        int skipped = 0
        try {
            if (f.getName().toLowerCase().endsWith(".xml")) {
                def res = readLinesXml(f)
                rois.addAll(res[0])
                skipped = res[1] as int
            } else if (f.getName().toLowerCase().endsWith(".roi")) {
                def r = RoiDecoder.open(f.getAbsolutePath())
                if (r != null) rois << r
            } else {
                def zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(f)))
                try {
                    def entry
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!entry.getName().toLowerCase().endsWith(".roi")) continue
                        def bytes = zis.readAllBytes()
                        def r = RoiDecoder.openFromByteArray(bytes)
                        if (r != null) rois << r
                    }
                } finally {
                    zis.close()
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "読み込めませんでした:\n" + ex.getMessage())
            return
        }
        if (rois.isEmpty()) { JOptionPane.showMessageDialog(frame, "線が見つかりませんでした"); return }

        def ov = imp.getOverlay() ?: new Overlay()
        rois.each { ov.add(styledCopy(it as Roi)) }
        imp.setOverlay(ov)
        def msg = "${rois.size()} 本の線を読み込みました"
        if (skipped > 0) msg += "（読めなかったもの ${skipped} 本）"
        IJ.showStatus(msg.toString())
    }

    // ------------------------------------------------------------ 線の XML
    /*
     * <EasyCellCounter_Lines>
     *   <Image_Properties><Image_Filename>…</Image_Filename></Image_Properties>
     *   <Lines>
     *     <Line kind="line|arrow|text|rectangle|oval|point|polygon" roiType="5"
     *           color="#ffff00" width="1.000" c="0" z="0" t="0" position="0">
     *       <Point x="…" y="…"/> …             （直線・矢印・折れ線・多角形・点）
     *       <Bounds x="…" y="…" width="…" height="…"/>   （四角・楕円・テキスト）
     *       <Arrow style="0" headSize="10" doubleHeaded="false"/>
     *       <Text font="SansSerif" size="18" style="0">文字</Text>
     *     </Line>
     *   </Lines>
     * </EasyCellCounter_Lines>
     */
    static String num(double v) {
        return String.format(Locale.ROOT, "%.3f", v)
    }

    static String kindOf(Roi r) {
        if (r instanceof Arrow) return "arrow"
        if (r instanceof TextRoi) return "text"
        if (r instanceof Line) return "line"
        if (r instanceof OvalRoi) return "oval"
        if (r instanceof PointRoi) return "point"
        if (r instanceof PolygonRoi) return "polygon"
        if (r.getType() == Roi.RECTANGLE) return "rectangle"
        return "polygon"   // その他の形は輪郭の点列として保存
    }

    static String pointXml(double x, double y) {
        return '      <Point x="' + num(x) + '" y="' + num(y) + '"/>\n'
    }

    static String boundsXml(java.awt.geom.Rectangle2D b) {
        return '      <Bounds x="' + num(b.getX()) + '" y="' + num(b.getY()) +
               '" width="' + num(b.getWidth()) + '" height="' + num(b.getHeight()) + '"/>\n'
    }

    static String lineToXml(Roi r) {
        def k = kindOf(r)
        def sb = new StringBuilder()
        sb << '    <Line kind="' << k << '" roiType="' << r.getType() << '"'
        if (r.getStrokeColor() != null) sb << ' color="' << Colors.colorToString(r.getStrokeColor()) << '"'
        sb << ' width="' << num(r.getStrokeWidth()) << '"'
        sb << ' c="' << r.getCPosition() << '" z="' << r.getZPosition() << '" t="' << r.getTPosition() << '"'
        sb << ' position="' << r.getPosition() << '"'
        if (r.getName() != null) sb << ' name="' << xmlEsc(r.getName()) << '"'
        sb << '>\n'
        switch (k) {
            case "line":
            case "arrow":
                def fp = ((Line) r).getFloatPoints()
                sb << pointXml(fp.xpoints[0], fp.ypoints[0]) << pointXml(fp.xpoints[1], fp.ypoints[1])
                if (k == "arrow") {
                    def a = (Arrow) r
                    sb << '      <Arrow style="' << a.getStyle() << '" headSize="' << num(a.getHeadSize()) <<
                          '" doubleHeaded="' << a.getDoubleHeaded() << '"/>\n'
                }
                break
            case "text":
                def tr = (TextRoi) r
                def f = tr.getCurrentFont()
                sb << boundsXml(r.getFloatBounds())
                sb << '      <Text font="' << xmlEsc(f.getName()) << '" size="' << f.getSize() <<
                      '" style="' << f.getStyle() << '">' << xmlEsc(tr.getText()) << '</Text>\n'
                break
            case "rectangle":
            case "oval":
                sb << boundsXml(r.getFloatBounds())
                break
            default:
                def poly = r.getFloatPolygon()
                for (int i = 0; i < poly.npoints; i++) sb << pointXml(poly.xpoints[i], poly.ypoints[i])
        }
        sb << '    </Line>\n'
        return sb.toString()
    }

    void writeLinesXml(File out, java.util.List<Roi> rois, String root = "EasyCellCounter_Lines") {
        def sb = new StringBuilder()
        sb << '<?xml version="1.0" encoding="UTF-8"?>\n'
        sb << '<' << root << '>\n'
        sb << '  <Image_Properties>\n'
        sb << '    <Image_Filename>' << xmlEsc(file.getName()) << '</Image_Filename>\n'
        sb << '  </Image_Properties>\n'
        sb << '  <Lines>\n'
        rois.each { Roi r -> sb << lineToXml(r) }
        sb << '  </Lines>\n'
        sb << '</' << root << '>\n'
        out.setText(sb.toString(), "UTF-8")
    }

    /** [線のリスト, 読めなかった数] */
    def readLinesXml(File f, String root = "EasyCellCounter_Lines") {
        def doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        if (doc.getElementsByTagName(root).getLength() == 0) {
            throw new IllegalArgumentException(root.endsWith("Regions") ?
                "このツールで保存したリージョンのXMLではないようです" :
                "このツールで保存した線のXMLではないようです")
        }
        def nodes = doc.getElementsByTagName("Line")
        def rois = []
        int skipped = 0
        for (int i = 0; i < nodes.getLength(); i++) {
            try {
                def r = roiFromXml((Element) nodes.item(i))
                if (r != null) rois << r else skipped++
            } catch (Exception ignore) {
                skipped++
            }
        }
        return [rois, skipped]
    }

    static Element firstChild(Element el, String tag) {
        def nodes = el.getElementsByTagName(tag)
        return nodes.getLength() > 0 ? (Element) nodes.item(0) : null
    }

    static double attr(Element el, String name, double dflt) {
        def v = el.getAttribute(name)
        return (v == null || v.isEmpty()) ? dflt : Double.parseDouble(v)
    }

    static Roi roiFromXml(Element el) {
        String k = el.getAttribute("kind")
        def ptNodes = el.getElementsByTagName("Point")
        int n = ptNodes.getLength()
        float[] xs = new float[n], ys = new float[n]
        for (int i = 0; i < n; i++) {
            def p = (Element) ptNodes.item(i)
            xs[i] = (float) attr(p, "x", 0)
            ys[i] = (float) attr(p, "y", 0)
        }
        def b = firstChild(el, "Bounds")
        Roi r = null
        switch (k) {
            case "line":
                if (n >= 2) r = new Line((double) xs[0], (double) ys[0], (double) xs[1], (double) ys[1])
                break
            case "arrow":
                if (n >= 2) {
                    def a = new Arrow((double) xs[0], (double) ys[0], (double) xs[1], (double) ys[1])
                    def ae = firstChild(el, "Arrow")
                    if (ae != null) {
                        a.setStyle((int) attr(ae, "style", a.getStyle()))
                        a.setHeadSize(attr(ae, "headSize", a.getHeadSize()))
                        a.setDoubleHeaded(ae.getAttribute("doubleHeaded") == "true")
                    }
                    r = a
                }
                break
            case "text":
                def te = firstChild(el, "Text")
                if (b != null && te != null) {
                    def font = new Font(te.getAttribute("font") ?: "SansSerif",
                        (int) attr(te, "style", Font.PLAIN), (int) attr(te, "size", 18))
                    r = new TextRoi(attr(b, "x", 0), attr(b, "y", 0), te.getTextContent(), font)
                }
                break
            case "rectangle":
                if (b != null) r = new Roi(attr(b, "x", 0), attr(b, "y", 0), attr(b, "width", 1), attr(b, "height", 1))
                break
            case "oval":
                if (b != null) r = new OvalRoi(attr(b, "x", 0), attr(b, "y", 0), attr(b, "width", 1), attr(b, "height", 1))
                break
            case "point":
                if (n >= 1) r = new PointRoi(xs, ys, n)
                break
            default:
                if (n >= 2) {
                    int type = (int) attr(el, "roiType", Roi.POLYGON)
                    def polyTypes = [Roi.POLYGON, Roi.FREEROI, Roi.TRACED_ROI, Roi.POLYLINE, Roi.FREELINE, Roi.ANGLE]
                    if (!polyTypes.contains(type)) type = Roi.POLYGON
                    r = new PolygonRoi(xs, ys, n, type)
                }
        }
        if (r == null) return null

        def col = el.getAttribute("color")
        r.setStrokeColor(col ? Colors.decode(col, Roi.getColor()) : Roi.getColor())
        r.setStrokeWidth(attr(el, "width", 1))
        def name = el.getAttribute("name")
        if (name) r.setName(name)
        int c = (int) attr(el, "c", 0), z = (int) attr(el, "z", 0), t = (int) attr(el, "t", 0)
        int pos = (int) attr(el, "position", 0)
        if (c > 0 || z > 0 || t > 0) r.setPosition(c, z, t)
        else if (pos > 0) r.setPosition(pos)
        return r
    }

    // ------------------------------------------------------------ Save All
    /**
     * 全部まとめて保存。保存先は画像と同じフォルダ（変更も可）。
     *   マーカー(XML・CSV) / 画像 / B&C / 線 / カウントCSV
     */
    void saveAll() {
        File dir = file.getParentFile()
        while (true) {
            def plan = saveAllPlan(dir)
            def existing = plan.findAll { it[1].exists() }
            def msg = new StringBuilder("次のファイルを保存します:\n${dir.getAbsolutePath()}\n\n")
            plan.each { msg << "  ・" << it[1].getName() << (it[1].exists() ? "（上書き）" : "") << "\n" }
            if (markers.isEmpty()) msg << "\n※ マーカーが0個なので、マーカーのファイルは作りません"
            if (linesToSave().isEmpty()) msg << "\n※ 線がないので、線のファイルは作りません"
            Object[] opts = ["保存", "フォルダを変更…", "キャンセル"] as Object[]
            int ans = JOptionPane.showOptionDialog(frame, msg.toString(), "Save All",
                JOptionPane.DEFAULT_OPTION, existing ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE,
                null, opts, opts[0])
            if (ans == 1) {
                def fc = new JFileChooser(dir)
                fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
                fc.setDialogTitle("保存先フォルダを選択")
                if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) dir = fc.getSelectedFile()
                continue
            }
            if (ans != 0) return
            break
        }

        def done = []
        def failed = []
        saveAllPlan(dir).each { item ->
            String label = item[0]
            File out = item[1]
            Closure writer = item[2]
            try {
                def ok = writer(out)
                if (ok == false) failed << label else done << out.getName()
            } catch (Exception ex) {
                failed << "${label}（${ex.getMessage()}）".toString()
            }
        }
        def msg = "保存しました（${dir.getAbsolutePath()}）\n\n" + done.collect { "  ・" + it }.join("\n")
        if (failed) msg += "\n\n保存できなかったもの:\n" + failed.collect { "  ・" + it }.join("\n")
        IJ.showStatus("Save All: ${done.size()} 個保存しました".toString())
        JOptionPane.showMessageDialog(frame, msg.toString())
    }

    /** Save All で保存するもの: [ラベル, 保存先, 書き出し処理] */
    def saveAllPlan(File dir) {
        def b = baseName()
        def plan = []
        if (!markers.isEmpty()) {
            plan << ["マーカー (XML)", new File(dir, b + "_markers.xml"), { File f -> writeMarkersXml(f) }]
            plan << ["マーカー (CSV)", new File(dir, b + "_markers.csv"), { File f -> writeMarkersCsv(f) }]
        }
        plan << ["画像", new File(dir, b + "_markers" + lastImageExt), { File f -> writeFlatImage(f) }]
        plan << ["B&C", new File(dir, b + "_display.json"), { File f -> writeDisplayJson(f) }]
        def rois = linesToSave()
        if (!rois.isEmpty()) {
            plan << ["線", new File(dir, b + "_lines.xml"), { File f -> writeLinesXml(f, rois) }]
        }
        if (!regions.isEmpty()) {
            def regs = new ArrayList(regions)
            plan << ["リージョン", new File(dir, b + "_regions.xml"), { File f -> writeLinesXml(f, regs, "EasyCellCounter_Regions") }]
        }
        plan << ["カウント (CSV)", new File(dir, b + "_counts.csv"), { File f -> writeCountsCsv(f) }]
        return plan
    }

    // ============================================================ Channels
    JPanel buildChannels() {
        def p = new JPanel()
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS))
        p.setBorder(BorderFactory.createTitledBorder("Channels"))

        modeCombo = new JComboBox<String>(["Composite", "Color", "Grayscale"] as String[])
        modeCombo.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT)
        modeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, modeCombo.getPreferredSize().height as int))
        modeCombo.addActionListener({ e -> if (!syncing) setMode(modeCombo.getSelectedIndex() + 1) } as ActionListener)
        p.add(modeCombo)
        p.add(Box.createVerticalStrut(4))

        int nc = imp.getNChannels()
        for (int k = 1; k <= nc; k++) {
            final int c = k
            def row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0))
            row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT)
            def sw = swatch(channelColor(c))
            def cb = new JCheckBox("Channel " + c, true)
            cb.addActionListener({ e -> if (!syncing) onChannelBox(c) } as ActionListener)
            row.add(sw)
            row.add(cb)
            channelSwatches << sw
            channelBoxes << cb
            p.add(row)
        }
        if (!imp.isComposite()) {
            modeCombo.setEnabled(false)
            channelBoxes.each { it.setEnabled(false) }
        }
        return p
    }

    Color channelColor(int c) {
        if (imp.isComposite()) {
            def lut = ((CompositeImage) imp).getChannelLut(c)
            if (lut != null) return new Color(lut.getRGB(255))
        }
        return Color.WHITE
    }

    void setMode(int mode) {
        if (!imp.isComposite()) return
        ((CompositeImage) imp).setMode(mode)
        imp.updateAndDraw()
        refreshDisplayPanels()
    }

    void onChannelBox(int c) {
        if (!imp.isComposite()) return
        def ci = (CompositeImage) imp
        if (ci.getMode() == IJ.COMPOSITE) {
            boolean[] active = ci.getActiveChannels()
            active[c - 1] = channelBoxes[c - 1].isSelected()
            ci.updateAndDraw()
        } else {
            imp.setC(c)        // Color / Grayscale ではそのチャンネルに切り替え
            imp.updateAndDraw()
        }
        refreshDisplayPanels()
    }

    void refreshChannels() {
        if (!imp.isComposite()) return
        def ci = (CompositeImage) imp
        int mode = ci.getMode()
        modeCombo.setSelectedIndex(Math.max(0, Math.min(2, mode - 1)))
        boolean[] active = ci.getActiveChannels()
        for (int i = 0; i < channelBoxes.size(); i++) {
            boolean on = (mode == IJ.COMPOSITE) ? active[i] : (imp.getC() == i + 1)
            channelBoxes[i].setSelected(on)
            channelSwatches[i].setBackground(channelColor(i + 1))
        }
    }

    // ============================================================ B&C
    JPanel buildBC() {
        def p = new JPanel()
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS))
        p.setBorder(BorderFactory.createTitledBorder("B&C"))

        int nc = imp.getNChannels()
        bcChannelCombo = new JComboBox<String>((1..nc).collect { "Channel " + it } as String[])
        bcChannelCombo.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT)
        bcChannelCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, bcChannelCombo.getPreferredSize().height as int))
        bcChannelCombo.setToolTipText("調整するチャンネル")
        bcChannelCombo.setEnabled(nc > 1)
        bcChannelCombo.addActionListener({ e ->
            if (!syncing) {
                selectBcChannel()
                refreshDisplayPanels()
            }
        } as ActionListener)
        p.add(bcChannelCombo)
        p.add(Box.createVerticalStrut(4))

        histPanel = new HistogramPanel()
        histPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT)
        p.add(histPanel)

        def labels = new JPanel(new BorderLayout())
        labels.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT)
        histMinLabel = new JLabel("0")
        histMaxLabel = new JLabel("0")
        labels.add(histMinLabel, BorderLayout.WEST)
        labels.add(histMaxLabel, BorderLayout.EAST)
        labels.setMaximumSize(new Dimension(Integer.MAX_VALUE, labels.getPreferredSize().height as int))
        p.add(labels)

        minSlider = slider(p, "Minimum") { int v -> def r = range(); setRange(fromSlider(v), r[1]) }
        maxSlider = slider(p, "Maximum") { int v -> def r = range(); setRange(r[0], fromSlider(v)) }
        brightSlider = slider(p, "Brightness") { int v ->
            // 右に動かすほど明るく（表示範囲を下へずらす）
            def r = range()
            double width = r[1] - r[0]
            double center = dataMax - v / (double) SL * (dataMax - dataMin)
            setRange(center - width / 2, center + width / 2)
        }
        contrastSlider = slider(p, "Contrast") { int v ->
            // 右に動かすほどコントラスト高く（表示範囲を狭める）
            def r = range()
            double center = (r[0] + r[1]) / 2
            double width = (dataMax - dataMin) * Math.pow(10, (SL / 2 - v) / (SL / 2.0))
            setRange(center - width / 2, center + width / 2)
        }

        def btns = new JPanel(new GridLayout(2, 2, 4, 4))
        btns.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT)
        btns.add(button("Auto", true) { autoContrast() })
        btns.add(button("Reset", true) { setRange(dataMin, dataMax) })
        btns.add(button("Set", true) { setDialog() })
        def apply = button("Apply", false)
        apply.setToolTipText("カウント中は元データを変えないよう無効にしています")
        btns.add(apply)
        p.add(Box.createVerticalStrut(4))
        p.add(btns)
        return p
    }

    JSlider slider(JPanel p, String label, Closure onChange) {
        def s = new JSlider(0, SL, (int) (SL / 2))
        s.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT)
        s.addChangeListener({ ChangeEvent e ->
            if (!syncing) onChange(s.getValue())
        } as ChangeListener)
        p.add(s)
        def l = new JLabel(label, SwingConstants.CENTER)
        l.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT)
        l.setMaximumSize(new Dimension(Integer.MAX_VALUE, l.getPreferredSize().height as int))
        p.add(l)
        return s
    }

    /** B&C で選んでいるチャンネル（1始まり） */
    int bcChannel() {
        return Math.max(0, bcChannelCombo.getSelectedIndex()) + 1
    }

    /** Composite 以外（1チャンネルずつ表示）のモードか */
    boolean isSingleChannelView() {
        return imp.isComposite() && ((CompositeImage) imp).getMode() != IJ.COMPOSITE
    }

    /**
     * B&C の対象チャンネルを画像の「現在チャンネル」にする。
     * ImageJ 本家の B&C も現在チャンネルだけを調整する仕組みなので、それに合わせる。
     * setC の直後に一度描画して、チャンネル切替の処理を先に済ませておく
     * （済ませないと、次の表示範囲の変更が切替処理で上書きされてしまう）。
     */
    void selectBcChannel() {
        int c = bcChannel()
        if (imp.getC() != c) {
            boolean prev = syncing
            syncing = true
            try {
                imp.setC(c)
                imp.updateAndDraw()
            } finally {
                syncing = prev
            }
        }
    }

    /** 対象チャンネル（= 現在チャンネル）の表示範囲 [min, max] */
    double[] range() {
        selectBcChannel()
        return [imp.getDisplayRangeMin(), imp.getDisplayRangeMax()] as double[]
    }

    double fromSlider(int v) {
        return dataMin + v / (double) SL * (dataMax - dataMin)
    }

    int toSlider(double val) {
        double span = (dataMax - dataMin) ?: 1
        return (int) Math.max(0, Math.min(SL, Math.round((val - dataMin) / span * SL)))
    }

    void setRange(double mn, double mx) {
        double minWidth = Math.max(1e-6, (dataMax - dataMin) / SL)
        if (mx - mn < minWidth) mx = mn + minWidth
        syncing = true
        try {
            selectBcChannel()
            imp.setDisplayRange(mn, mx)
            // updateAllChannelsAndDraw() は全チャンネルを今の範囲にそろえてしまうので使わない
            imp.updateAndDraw()
        } finally {
            syncing = false
        }
        refreshBC()
    }

    /** B&C で選んだチャンネル（と現在のZ・T）の統計。ピクセルは変えないのでキャッシュする */
    ImageStatistics currentStats() {
        int c = bcChannel()
        def key = "${c},${imp.getZ()},${imp.getT()}".toString()
        def st = statsCache[key]
        if (st == null) {
            def ip = imp.getStack().getProcessor(imp.getStackIndex(c, imp.getZ(), imp.getT()))
            st = ip.getStats()
            statsCache[key] = st
        }
        return st
    }

    /** ヒストグラム横軸の範囲 [min, max] */
    static double[] histRange(ImageStatistics st) {
        if (st.histMax > st.histMin) return [st.histMin, st.histMax] as double[]
        return [0d, (double) st.histogram.length - 1] as double[]
    }

    void autoContrast() {
        def st = currentStats()
        int[] h = st.histogram
        long total = 0
        h.each { total += it }
        long limit = (long) (total * 0.0035 / 2)   // 両端あわせて 0.35% を飽和させる（ImageJ の Auto と同程度）
        int lo = 0, hi = h.length - 1
        long acc = 0
        while (lo < h.length - 1 && (acc += h[lo]) <= limit) lo++
        acc = 0
        while (hi > 0 && (acc += h[hi]) <= limit) hi--
        if (hi <= lo) { setRange(dataMin, dataMax); return }
        def hr = histRange(st)
        double bin = (hr[1] - hr[0]) / h.length
        setRange(hr[0] + lo * bin, hr[0] + (hi + 1) * bin)
    }

    void setDialog() {
        def r = range()
        def gd = new GenericDialog("表示範囲を設定")
        gd.addNumericField("Minimum", r[0], 2)
        gd.addNumericField("Maximum", r[1], 2)
        gd.showDialog()
        if (gd.wasCanceled()) return
        setRange(gd.getNextNumber(), gd.getNextNumber())
    }

    void refreshBC() {
        if (histPanel == null) return
        // 画像側でチャンネルを動かしたら、B&C の選択もそれに合わせる（先にそろえておく）
        if (bcChannelCombo.getSelectedIndex() != imp.getC() - 1) {
            syncing = true
            try {
                bcChannelCombo.setSelectedIndex(imp.getC() - 1)
            } finally {
                syncing = false
            }
        }
        def st = currentStats()
        dataMin = st.min
        dataMax = st.max > st.min ? st.max : st.min + 1
        def r = range()

        syncing = true
        try {
            histPanel.hist = st.histogram
            def hr = histRange(st)
            histPanel.hMin = hr[0]
            histPanel.hMax = hr[1]
            histPanel.dMin = r[0]
            histPanel.dMax = r[1]
            def hc = channelColor(bcChannel())
            histPanel.barColor = (hc == Color.WHITE) ? Color.GRAY : hc
            histPanel.repaint()
            histMinLabel.setText(fmt(dataMin))
            histMaxLabel.setText(fmt(dataMax))

            minSlider.setValue(toSlider(r[0]))
            maxSlider.setValue(toSlider(r[1]))
            double span = (dataMax - dataMin) ?: 1
            double center = (r[0] + r[1]) / 2
            brightSlider.setValue((int) Math.max(0, Math.min(SL, Math.round((dataMax - center) / span * SL))))
            double width = Math.max(1e-9, r[1] - r[0])
            double cv = SL / 2 - (SL / 2.0) * Math.log10(width / span)
            contrastSlider.setValue((int) Math.max(0, Math.min(SL, Math.round(cv))))
        } finally {
            syncing = false
        }
    }

    static String fmt(double v) {
        return (v == Math.rint(v)) ? String.valueOf((long) v) : String.format("%.2f", v)
    }

    void refreshDisplayPanels() {
        if (imp == null || imp.getWindow() == null) return
        refreshScale()
        syncing = true
        try {
            refreshChannels()
        } finally {
            syncing = false
        }
        refreshBC()
    }

    // ============================================================ CSV 出力
    /**
     * 縦向きの集計CSV:
     *   folder / file / image の情報 → 空行 → type,count の表 → total
     * Excel で文字化けしないよう UTF-8 (BOM付き) で保存する。
     */
    void exportCsv() {
        File out = chooseSaveFile("CSV を保存", baseName() + "_counts.csv", ".csv")
        if (out == null) return
        try {
            writeCountsCsv(out)
            IJ.showStatus("CSV を保存しました: " + out.getName())
            JOptionPane.showMessageDialog(frame, "保存しました\n" + out.getAbsolutePath())
        } catch (Exception ex) {
            IJ.handleException(ex)
        }
    }

    void writeCountsCsv(File out) {
        def rows = []
        rows << ["folder", file.getParentFile()?.getAbsolutePath() ?: ""]
        rows << ["file", file.getName()]
        rows << ["image", imp?.getTitle() ?: ""]
        def cal = imp.getCalibration()
        rows << ["pixel size", cal.scaled() ? num4(cal.pixelWidth) + " x " + num4(cal.pixelHeight) : "1",
                 cal.scaled() ? unitLabel(cal.getUnit()) + "/px" : "px (not calibrated)"]
        rows << []
        rows << ["type", "count"]
        int total = 0
        for (int i = 0; i < N_TYPES; i++) {
            rows << [typeName(i), String.valueOf(counts[i])]
            total += counts[i]
        }
        rows << ["total", String.valueOf(total)]

        // リージョンごと（横向きの表）
        if (!regions.isEmpty()) {
            rows << []
            def head = ["region", "area (" + areaUnit() + ")"]
            for (int i = 0; i < N_TYPES; i++) head << typeName(i)
            head << "total"
            boolean dens = perMm2(1, 1.0d) != null
            if (dens) for (int i = 0; i < N_TYPES; i++) head << typeName(i) + " /mm2"
            rows << head
            regionSummary().each { r ->
                def row = [r[0] as String, String.format(Locale.ROOT, "%.2f", r[2] as double)]
                int[] c = r[3] as int[]
                for (int i = 0; i < N_TYPES; i++) row << String.valueOf(c[i])
                row << String.valueOf(r[4])
                if (dens) for (int i = 0; i < N_TYPES; i++) {
                    row << String.format(Locale.ROOT, "%.1f", perMm2(c[i], r[2] as double))
                }
                rows << row
            }
        }
        writeCsv(out, rows)
    }

    static String csvCell(String s) {
        if (s == null) return ""
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\""
        }
        return s
    }
}


new EasyLoader().show()