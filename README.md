# Easy Cell Counter

A Fiji (ImageJ) script for counting cells in fluorescence images, built to replace the built-in **Cell Counter**. It works especially well with multi-channel Zeiss **CZI** files.

- Drag and drop images to open them as composites. There is no need to split and merge channels.
- The pixel size is read from the file and shown in the window. Areas and densities use it.
- Rename cell types freely, e.g. `tdTomato+(NeuN+)`, and show or hide each type's markers.
- Count by clicking, or use **Auto Detect** with a live preview: **StarDist** (deep learning) or **Find Maxima** with a background mask. Then correct the result by hand.
- Classify cells as marker-positive using a second channel, with a manual or automatic (Otsu) threshold.
- Define **regions** (ROIs) before or after counting. You get live counts, area and density for each region.
- Adjust channels and brightness/contrast one channel at a time. The pixel data is never changed.
- Draw lines and shapes and keep them when you save.
- Save and reload everything: markers, image, display settings, lines, regions and counts.

---

## Contents

1. [Installation](#installation)
2. [Quick start](#quick-start)
3. [Opening images](#opening-images)
4. [The counter window](#the-counter-window)
5. [Counting by hand](#counting-by-hand)
6. [Auto Detect](#auto-detect)
7. [Regions (ROI-based counting)](#regions-roi-based-counting)
8. [Scale (pixel size)](#scale-pixel-size)
9. [Lines and annotations](#lines-and-annotations)
10. [Saving and loading](#saving-and-loading)
11. [File formats](#file-formats)
12. [Measurement methods](#measurement-methods)
13. [Reporting recommendations](#reporting-recommendations)
14. [Settings that are remembered](#settings-that-are-remembered)
15. [Limitations](#limitations)
16. [Troubleshooting](#troubleshooting)
17. [Japanese UI labels](#japanese-ui-labels)

> The tool's dialogs and messages are in Japanese. This README uses English equivalents; see [Japanese UI labels](#japanese-ui-labels) for how they map.

---

## Installation

### 1. The script

1. Copy `Easy_Cell_Counter.groovy` into:
   ```
   Fiji.app/scripts/Plugins/
   ```
   You can also use a subfolder, e.g. `Fiji.app/scripts/Plugins/Counting/`, to get a submenu.
2. Restart Fiji.
3. Run it from **Plugins › Easy Cell Counter**.

> **The extension must be `.groovy`.** Fiji picks the language from the extension. A file saved as `.py` runs as Python and fails with a `SyntaxError`. On Windows, turn on *View › File name extensions* in Explorer so the file doesn't end up named `Easy_Cell_Counter.groovy.py`.

### 2. StarDist (needed only for the StarDist detection method)

Do this once on each computer:

1. **Help › Update…**
2. **Manage update sites**
3. Tick **CSBDeep** and **StarDist**, then **Apply and close**.
4. **Apply changes**, then restart Fiji.

Without StarDist the tool still works. Choose **Find Maxima** as the detection method. If you choose StarDist when it isn't installed, these steps are shown when you click OK.

### Requirements

- Fiji with Bio-Formats. A current Fiji includes Bio-Formats and Groovy.
- Supported formats: `.czi`, `.tif`, `.tiff`, `.nd2`, `.lif`, `.lsm`, `.oib`, `.oir`, `.ims`, `.vsi`, `.png`, `.jpg`, `.jpeg`.

---

## Quick start

1. **Plugins › Easy Cell Counter**.
2. Drop a `.czi` file (or a folder) onto the window and double-click the image. The counter window opens next to the image.
3. Check that the **Scale** row says *[from file]*.
4. Rename the types in **Counters**, e.g. `Hoechst`, `GFP+ (Hoechst+)`.
5. **Auto Detect…** → StarDist, nuclear channel → check the preview → **OK**.
6. Correct the result: click to add a marker, **Shift+click** to remove one.
7. *(Optional)* Draw an area and click **Add Region**. The region's counts appear in the table under Counters.
8. **Save › Save All**.

---

## Opening images

The loader window has these parts:

| Element | What it does |
|---|---|
| Drop area | Drop images and/or folders. For a folder, only supported images are added. Duplicates are ignored. |
| File list | Double-click to open. Images you have opened are marked ✓. |
| *Max-project Z stacks when opening* | If checked, a Z stack is opened as a maximum-intensity projection. |
| **Open** / **Add…** / **Remove** / **Clear** | Open the selected image, add files, remove the selected entry, clear the list. |

- Images open through **Bio-Formats** as **composite** hyperstacks, using the channel colours stored in the file.
- If a file holds several series (CZI scenes, pyramid levels, …), a dialog lists them so you can choose one.
- The **pixel size from the file** is applied to the image, and it is kept after Z projection and composite conversion.
- Once an image is open, the loader closes and the counter window opens. To open another image, run the plugin again.

---

## The counter window

### Image location (top)

| Row | Contents |
|---|---|
| Folder / File | Where the image came from. You can select and copy the text. |
| **Scale** | The pixel size, the image size in physical units, the Z step (for stacks) and where the scale came from: **[from file]** or **[manual]**. It turns orange when the image is not calibrated. **Set…** lets you enter the scale by hand. |

### Counters (left, top)

There are 8 cell types. Each row has:

- a radio button that selects the active type,
- a colour swatch showing the marker colour,
- an **editable name** that is remembered between sessions,
- the count for the **whole image**,
- a **visibility checkbox** that shows or hides that type's markers.

### Regions table (left, bottom)

For each region it shows the area, the count of each type in use, and the total. The last row is **(whole image)**. Tick **Show per mm²** to show densities instead. The table updates whenever markers or regions change.

### Actions (middle)

| Group | Control | What it does |
|---|---|---|
| **Auto** | **Auto Detect…** | Semi-automatic detection. See [Auto Detect](#auto-detect). |
| | **Undo** | Undoes the last marker change: click, delete, reset, load, auto-detect or Remove Outside. Keeps up to 100 steps. |
| | **Delete** | Removes the most recently placed marker of the selected type. |
| | **Delete Mode** | While checked, a click deletes the nearest marker. |
| | **Results** | Opens the counts per type in a results table, and the regions table too if there are regions. |
| | **Reset** | Removes all markers, after asking. |
| | **Show Markers** | Shows or hides all markers. Clicks don't count while markers are hidden. |
| | **Show Numbers** | Shows the type number next to each marker. |
| | **Show All** | In a stack, shows the markers from every slice. |
| **Regions** | **Add Region** | Saves the current area selection as a named region. |
| | **Remove Region…** | Removes one region, chosen by name. |
| | **Clear Regions** | Removes all regions, after asking. |
| | **Remove Outside** | Deletes every marker outside all regions, after asking. Can be undone. |
| | **Show Regions** | Shows or hides region outlines and names. |
| **Lines** | **Add Line** / **Remove Line** / **Clear Lines** | Turns the current selection into a permanent annotation; removes the last one; removes all of them. |
| **Save** | **Save All** | Saves everything at once. |
| | **Markers** / **Image** / **B&C** / **Lines** / **Regions** / **Counts (CSV)** | Saves each item separately. |
| **Load** | **Markers** / **B&C** / **Lines** / **Regions** | Loads each item. |

### Channels and B&C (right)

- **Channels**: a display mode (Composite / Color / Grayscale) and a checkbox for each channel.
- **B&C**: choose a channel, then use the histogram, the Minimum, Maximum, Brightness and Contrast sliders, and Auto, Reset and Set. **Only the selected channel changes.** **Apply is disabled on purpose**: everything here changes the display only and never the pixel values, so intensity measurements stay valid.

---

## Counting by hand

- **Click** to add a marker of the selected type. **Drag** to pan.
- **Shift+click**, or a click in **Delete Mode**, removes the nearest visible marker.
- Clicks count only with the **Hand tool**. The tool switches to it automatically when the window opens and after Add Line or Add Region.
- You can't add markers to a hidden type, and hidden markers are never deleted by a click.
- A marker belongs to the Z slice and time point where it was placed.
- When placing markers by hand, **click near the centre of the nucleus**. The centre decides which region the cell belongs to.

---

## Auto Detect

### Workflow

1. *(Optional)* Limit the area. If there is an area selection, only that area is used. If there is no selection but there are **regions**, all regions together are used. Otherwise the whole image is used.
2. Click **Auto Detect…** and set the parameters. The preview updates as you change them:
   - **StarDist**: the outlines of the nuclei that are kept, plus dashed circles at their centres.
   - **Find Maxima**: dashed circles, and the foreground mask in translucent yellow.
3. Click **OK**. The detections become ordinary markers. Correct them by hand. **Undo** reverts the whole run.

### Common parameters

| Parameter | Meaning |
|---|---|
| **Detection channel** | The channel the cells are found in, usually the nuclear stain. |
| **Target type** | The type the detections are added to. |
| **Detection method** | **StarDist (AI)** (the default) or **Find Maxima (classic)**. |
| **Replace existing markers of this type** | Before adding, removes markers of the target type on the current slice inside the counting area. Other types are never touched. |
| **Preview** | Turns the live preview on or off. |

### StarDist parameters

| Parameter | Default | Meaning / tips |
|---|---|---|
| **Probability threshold** (0–1) | 0.5 | Minimum object probability. Raise it if background is detected; lower it if nuclei are missed. |
| **Overlap threshold (NMS)** (0–1) | 0.4 | How much overlap is allowed between candidate nuclei. Usually leave it unchanged. |
| **Min area (px)** | 50 | Removes small objects such as puncta and debris. About ⅓ of a typical nucleus area is a good start. |
| **Max area (px)** | 0 (no limit) | Removes oversized objects. |

StarDist runs on the original image, **not** on a binarized one: it was trained on intensity images. Each run takes a few seconds. Changing the channel, the probability or the NMS starts a new run in the background, while the area and positivity settings update instantly.

### Find Maxima parameters

| Parameter | Meaning / tips |
|---|---|
| **Gaussian blur σ (px)** | Smoothing applied before peaks are searched. About half the nucleus radius, usually 3–5 px. A σ that is too large merges neighbouring nuclei. |
| **Prominence** | Minimum height of a peak above the saddle to its neighbours. **This is the main control.** For 16-bit images, start in the hundreds. |
| **Minimum intensity** | Removes dim peaks, measured on the blurred image. 0 turns it off. |
| **Count only inside the binarized foreground** | Background mask. Peaks outside the foreground are discarded. On by default. |
| **Threshold method** | Li (default), Triangle, Otsu, Huang, Default, Mean or Manual. Li and Triangle suit fluorescent nuclei; Otsu is stricter. The threshold actually used is shown in the dialog. |
| **Manual threshold** | Used only with the Manual method. |
| **Remove small particles (min area px)** | Foreground pieces smaller than this are removed from the mask. |

### Positive classification (both methods)

| Parameter | Meaning |
|---|---|
| **Positive channel** | The marker channel, e.g. GFP. **None** turns classification off. |
| **Threshold** | A cell is positive when its mean intensity in the positive channel is **≥ threshold**. |
| **Determine threshold automatically (Otsu, StarDist only)** | Applies Otsu's method to the per-nucleus mean intensities of the current image. |
| **Window radius (px)** | Find Maxima only: the size of the square window around the peak. |

With StarDist, the mean is taken over the **segmented nucleus itself**. The preview then shows `Positive: N / all nuclei M`, the median of the per-nucleus means, and the threshold used. If N equals M, the threshold isn't doing anything.

**Counting all nuclei and double-positive cells:**

| | All nuclei | Double-positive |
|---|---|---|
| Detection channel | Nuclear (e.g. Hoechst) | Nuclear (e.g. Hoechst) |
| Target type | e.g. Type 1 *Hoechst* | e.g. Type 2 *GFP+ (Hoechst+)* |
| Positive channel | **None** | **Marker (e.g. GFP)** |
| Threshold | — | > 0, or automatic |

Auto Detect remembers its last settings, including the positive channel. **Set the positive channel to None** before counting all nuclei.

---

## Regions (ROI-based counting)

### Two ways to work

- **Regions first:** draw areas and click **Add Region**, then run Auto Detect. With no selection, detection is limited to the regions.
- **Count first:** count the whole image, then draw and add regions. Region counts are always worked out from the current markers, so the order doesn't matter. **Remove Outside** keeps only the markers inside the regions, and it can be undone.

### Rules

- A marker belongs to a region when its **centre point** lies inside the region (pixel test). The drawn circle is only for display. On rectangle edges, the top and left edges count as inside and the bottom and right edges as outside.
- Each cell has exactly one centre, so with regions that touch, every cell is counted exactly once: never twice and never missed.
- Where regions **overlap**, a cell in the overlap is counted in both.
- Regions apply to every Z slice and time point. Markers from all slices are counted.
- Region names appear at the centre of each region, in orange.

### Outputs

- The **Regions table** in the window shows the area, the counts per type, the total, and optionally the density per mm².
- **Results** shows the same table as a results table.
- **Counts (CSV)** includes a per-region table. See [File formats](#file-formats).

---

## Scale (pixel size)

- Bio-Formats reads the pixel size from the file's metadata (for example CZI). It is applied when the image opens and kept after max projection and composite conversion.
- The **Scale** row shows it, e.g. `0.1235 µm/px (image 135.4 × 108.2 µm) [from file]`.
- If the file has no calibration, the row turns orange and says *not set*. Use **Set…** to enter the pixel width, the pixel height and the unit (`micron` for µm). Changes made in *Image › Properties* also appear here.
- Region areas and densities use this scale. Densities per mm² are available only when the unit is µm or mm.
- **Counts (CSV)** records the pixel size used.

---

## Lines and annotations

ImageJ's line and shape tools create a temporary **selection**. ImageJ keeps only one selection at a time, and it is not included in flattened images. To keep drawings:

1. Draw with any ImageJ tool: line, arrow, rectangle, oval, text, polyline, …
2. Click **Add Line**. The selection becomes a permanent annotation, and the tool switches back to the Hand tool.
3. **Save › Image** burns in markers, annotations and regions. A selection you haven't added yet is included too.

---

## Saving and loading

Save dialogs open in the image's folder with a suggested file name, and ask before overwriting. Load dialogs preselect the matching file if there is one.

### Save All

Shows the list of files and marks the ones that will be overwritten. You can change the destination folder before saving. It writes:

| File | Contents | Written when |
|---|---|---|
| `<image>_markers.xml` | Markers (Cell Counter XML) | there are markers |
| `<image>_markers.csv` | Markers (CSV) | there are markers |
| `<image>_markers.png` | Image with markers, annotations and regions burned in. Uses the format last chosen in Save › Image. | always |
| `<image>_display.json` | Display settings (B&C) | always |
| `<image>_lines.xml` | Annotations | there are annotations |
| `<image>_regions.xml` | Regions | there are regions |
| `<image>_counts.csv` | Counts, pixel size and per-region table | always |

### Individual save and load

| Item | Save formats | Load formats | Notes |
|---|---|---|---|
| Markers | XML (default), CSV | XML, CSV | XML files from the **built-in Cell Counter** load too. If markers already exist, you choose Replace, Add or Cancel. Type names are restored. |
| Image | PNG, TIFF, JPEG | — | Saves the current plane exactly as displayed. |
| B&C | JSON | JSON | Display mode, display range of each channel, and which channels are on. Settings can be loaded into a different image; only matching channel numbers are applied. |
| Lines | XML (default), ROI set `.zip` | XML, `.zip`, `.roi` | The `.zip` opens in ImageJ's ROI Manager. |
| Regions | XML (default), ROI set `.zip` | XML, `.zip`, `.roi` | Only area ROIs are accepted. Names are kept. |
| Counts | CSV | — | |

---

## File formats

All text files are UTF-8. CSV files include a BOM so that Excel shows non-ASCII text correctly.

### Counts CSV (`*_counts.csv`)

```
folder,D:\Confocal\20261012\...
file,Experiment-18091.czi
image,Experiment-18091.czi - Experiment-18091.czi #3
pixel size,0.1235 x 0.1235,µm/px

type,count
Hoechst,856
NeuN(GFP),412
...
total,1268

region,area (µm^2),Hoechst,NeuN(GFP),...,total,Hoechst /mm2,NeuN(GFP) /mm2,...
R1,1862.47,131,76,...,207,70336.6,40806.0,...
R2,1202.32,77,28,...,105,64042.9,23288.3,...
```

- The top block holds counts for the **whole image**.
- The region block holds **raw counts and area** for each region, followed by densities (count ÷ area, in mm²; 1 mm² = 10⁶ µm²). The density columns appear only when the scale is in µm or mm.

### Markers CSV (`*_markers.csv`)

```
x,y,z,t,type,type_name
123.50,456.50,1,1,1,Hoechst
```

Coordinates are in pixels, with sub-pixel precision. `z` and `t` start at 1.

### Markers XML (`*_markers.xml`)

Uses the same schema as ImageJ's Cell Counter (`CellCounter_Marker_File`), with integer coordinates and `MarkerZ` given as the stack index. Each type stores its `Name`.

### Display JSON (`*_display.json`)

```json
{ "image": "…", "mode": "Composite",
  "channels": [ { "channel": 1, "min": 120.0, "max": 3500.0, "active": true } ] }
```

### Lines and regions XML (`*_lines.xml`, `*_regions.xml`)

Both use the same element structure. The root element is `EasyCellCounter_Lines` or `EasyCellCounter_Regions`. Each `<Line>` stores its `kind` (line, arrow, rectangle, oval, text, point, polygon), its geometry (points or bounds), colour, stroke width, slice position and `name`.

---

## Measurement methods

### StarDist detection

1. The detection channel of the current Z/T plane is passed to the Fiji **StarDist 2D** plugin. Settings: model *Versatile (fluorescent nuclei)*; input normalized to the 1st–99.8th percentiles; probability threshold and NMS threshold as set by the user; 1 tile; nuclei touching the image border (2 px) excluded.
2. The output is a label image with one label per nucleus. For each label, the tool measures the **area** (pixel count), the **centroid**, and optionally the **mean intensity of the unsmoothed positive channel within the label**.
3. Nuclei outside the area limits, or with the centroid outside the counting area, are discarded. A marker is placed at each remaining centroid.
4. *(Optional)* A nucleus is positive if its mean is at least the threshold. In automatic mode, the threshold is computed with **Otsu's method** (256-bin histogram) over the per-nucleus means of all nuclei that passed step 3.

### Find Maxima detection

1. The detection channel of the current Z/T plane is converted to 32-bit and smoothed with a Gaussian filter (σ).
2. Local maxima are found with ImageJ's `MaximumFinder` (the same algorithm as *Process › Find Maxima…*) with the given **prominence**. Maxima touching the image border are excluded.
3. **Background mask (optional):** the detection channel is smoothed with σ = 1 px and thresholded with an ImageJ auto-threshold method (dark background) or a manual value. Foreground pieces (4-connected) smaller than the minimum area are removed. Only maxima inside the remaining foreground are kept. Detection itself uses the intensity image, not the mask, so touching nuclei can still be separated.
4. Maxima below the minimum intensity, or outside the counting area, are discarded.
5. *(Optional)* A cell is positive when the mean of the unsmoothed positive channel in a (2r+1) × (2r+1) px square centred on the maximum is at least the threshold.

### Region assignment and density

- A cell is assigned to a region when its **centre** (the nucleus centroid for StarDist, the intensity maximum for Find Maxima, the click position for manual markers) lies within the region.
- Area = number of pixels in the region × pixel width × pixel height, using the image calibration.
- Density = count ÷ area, given per mm².

### Example Methods text

> Nuclei were detected semi-automatically in Fiji using a custom script (Easy Cell Counter) that calls StarDist 2D (model "Versatile (fluorescent nuclei)", probability threshold 0.5, NMS threshold 0.4). Objects smaller than 50 px² were excluded. Cells were classified as GFP-positive when the mean GFP intensity within the segmented nucleus exceeded a threshold determined by Otsu's method on the per-nucleus intensity distribution of each image. Cells were assigned to a region if the nuclear centroid fell within the region boundary. Densities were calculated as cell counts divided by region area (pixel size 0.1235 µm, from the image metadata). The same parameters were used for all images, and all detections were visually inspected and manually corrected.

Replace the numbers with the values you actually used.

---

## Reporting recommendations

Converting units does not change your data. Misleading results come from **how regions are chosen and reported**:

- **Report raw counts and area with every density**, e.g. "131 cells in 1,862 µm² (70,300 cells/mm²)". A density measured in a small region describes **that region only**. It does not mean a full mm² was counted.
- **Define regions by anatomical criteria before counting** (e.g. distance from the pial surface, layer, rostro-caudal level). Draw them blind to the experimental group if you can. Don't place regions where the effect looks strongest.
- **Show the regions**: include images with region outlines (Save › Image) in the supplementary material, and deposit the region files (`_regions.xml` or `.zip`) with the raw images.
- **Sample several regions and sections per animal** and average them. Small regions vary a lot: roughly ±1/√n, so about ±9 % for 131 cells and ±21 % for 23 cells.
- **Use the animal as n**, not the region or the section.
- **Keep acquisition and analysis identical** across images: projection thickness, detection method and parameters, and how the threshold is chosen.
- Consider **ratios** (e.g. GFP+ / Hoechst+ in the same region). They are less sensitive to region size and placement than absolute densities.
- **Max projections** of thick stacks merge nuclei from different depths, which raises the apparent density. Compare only images with the same thickness and projection settings.

---

## Settings that are remembered

These are stored in Fiji's preferences (`IJ_Prefs.txt`). They are written when **Fiji closes normally**.

- The 8 type names.
- All Auto Detect settings: channel, method, StarDist parameters, Find Maxima parameters, mask settings, positive channel, threshold and auto-threshold, and the replace option.

These last only until the counter window is closed: marker visibility, regions (unless saved), and the image format last used in Save › Image.

---

## Limitations

- There are 8 cell types, and types can't be added or removed.
- Undo covers markers only. Regions, lines and display settings can't be undone.
- Undo is a button only; Ctrl+Z is not bound.
- Auto Detect works on the **current plane**. StarDist runs in 2D.
- Marker XML coordinates are rounded to whole pixels. Use CSV to keep sub-pixel positions.
- Closing the counter window does not reopen the loader.
- The automatic (Otsu) threshold assumes the image contains both positive and negative cells. With very few positive cells it can split the negative group. Always check the preview.
- Peak- and centroid-based detection can merge touching nuclei or split irregular ones. Visual inspection is still needed.

---

## Troubleshooting

| Problem | Cause / fix |
|---|---|
| `SyntaxError: mismatched input '{' expecting COLON` | The file has a `.py` extension. Rename it to `.groovy`. |
| "StarDist is not installed" | Add the **CSBDeep** and **StarDist** update sites (see [Installation](#installation)), or use Find Maxima. |
| The StarDist preview keeps saying "calculating…" | The first run loads the model, which takes a while. If it never finishes, check Fiji's Console for errors. |
| Clicks don't add markers | Use the **Hand tool**. Make sure **Show Markers** is on, the selected type is visible, and **Delete Mode** is off. |
| The double-positive count equals the nuclear count | The positive threshold is 0 or too low. Look at the median shown in the preview, or turn on the automatic threshold. |
| The nuclear count is too low after counting double-positives | The positive channel was still set. Set it to **None**. |
| Small bright dots are counted as cells | Raise **Min area** (StarDist) or **Remove small particles** (Find Maxima). |
| Background is detected (Find Maxima) | Turn on the background mask, raise Prominence, and use σ of 3–5. |
| One nucleus gets two markers (Find Maxima) | Raise Prominence or σ, or switch to StarDist. |
| Scale shows *not set* | The file has no calibration. Use **Set…**. |
| A line isn't in the saved image | Click **Add Line** before drawing the next selection. |
| Type names were lost | Fiji was not closed normally. |

---

## Japanese UI labels

| On screen | In this README |
|---|---|
| 画像の場所 / フォルダ / ファイル / スケール / 設定… | Image location / Folder / File / Scale / Set… |
| ［ファイルから取得］ / ［手動］ / 未設定 | [from file] / [manual] / not set |
| Zスタックは最大値投影して開く | Max-project Z stacks when opening |
| 1 mm² あたりの数で表示 | Show per mm² |
| （画像全体） | (whole image) |
| 検出に使うチャンネル | Detection channel |
| 追加先のタイプ | Target type |
| 検出方法 — StarDist（AI） / Find Maxima（従来） | Detection method — StarDist (AI) / Find Maxima (classic) |
| 確率しきい値 | Probability threshold |
| 重なりしきい値 NMS | Overlap threshold (NMS) |
| 最小面積 px（小さい点を除く） / 最大面積 px | Min area / Max area |
| ぼかし σ | Gaussian blur σ |
| Prominence（大きいほど厳しい） | Prominence |
| 最低輝度 | Minimum intensity |
| 二値化した前景の中だけ数える | Count only inside the binarized foreground |
| しきい値の決め方 / 手動しきい値 | Threshold method / Manual threshold |
| 小さい粒を除く（最小面積 px） | Remove small particles (min area px) |
| 陽性を見るチャンネル | Positive channel |
| しきい値（周りの平均輝度） | Threshold |
| しきい値を自動で決める（大津法） | Determine threshold automatically (Otsu) |
| 見る範囲の半径 | Window radius |
| このタイプの既存マーカーを置き換える | Replace existing markers of this type |
| プレビュー | Preview |
| 陽性: N / 全核 M 個 | Positive: N / all nuclei M |