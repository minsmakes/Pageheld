# Pageheld. — Book Studio Asset Pack Vol. 01 (prototype)

12 selectable assets: 3 cover textures, 6 stickers, 1 photo frame, 2 tapes. The 3 neutral tint sources are helper images, not separate catalog items.

All artwork originates from the generated concept sheet. Cutouts have real alpha channels, hand-traced silhouettes with lightly feathered edges; some light edge/shadow residue may remain. Original source crops are small and enlarged for this prototype. Material textures use mirrored tiling, not independently verified seamless textures. Inspect at actual tablet size before shipping. No baked book title or complete book cover. All assets are local PNGs.

Use `catalog.json` for stable IDs and relative file paths. Copy this folder into `app/src/main/assets/artwork/vol01/` and load through Android AssetManager. For a recolorable material use `tintSource` and multiply/overlay the neutral luminance texture with selected color. Clip to book cover. For frame use transparent window, render user photo beneath the frame. Store sticker transforms in normalized cover coordinates. Preserve catalog IDs in backup. No code changes are included.
