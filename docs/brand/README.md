# Keeps identity

User-selected four-tile file/gallery mark (8 October): three black rounded tiles and a vivid red upper-right tile with a slanted top edge, on white. The prior stacked-horizon direction is superseded. Native vector paths preserve clean edges and provide a matching monochrome icon. The original supplied raster/cutout are retained locally and ignored for source publication; distributed identity assets use the vector masters and their exports.

The foreground is scaled to 76% around the 108 dp viewport center after the user reported circular-mask clipping. The review sheet now simulates Android's actual central 72 dp adaptive viewport. Source and store exports retain square backgrounds; launchers supply their masks.

- `user-selected-logo.png`: supplied reference, unchanged.
- `user-logo-cutout.png`: built-in imagegen transparent background extraction. Tool prompt: remove white background and faint outer border; preserve the four tile shapes/spacing; solid black and red; no shadows, text or additions. Generated asset copied into this folder. Native vectors are used for launcher rendering.

- `keeps-icon.svg`: editable full-background vector master.
- `keeps-mark.svg`: transparent vector mark.
- `keeps-monochrome.svg`: single-color mark.
- Matching 512×512 PNG exports; `keeps-icon.png` is the opaque store-icon source.
- `keeps-icon-preview.png`: circle, rounded-square, themed and 32–96 px review sheet.
- Android vector foreground and monochrome resources, adaptive icons for API 26+ and themed icons for API 33+. The manifest uses the adaptive icon for both regular and round launchers.

Re-export with `python tools/create_brand_assets.py` (svglib, reportlab, PyMuPDF and Pillow). The PNGs are rasterized from the SVG master. Update the path/color definitions in that script to keep all exports and Android resources consistent.

Palette: black `#000000`, red `#FF001F`, white `#FFFFFF`. Keep the full adaptive background; launcher masks supply the outer shape. Do not pre-round the store-icon source.
