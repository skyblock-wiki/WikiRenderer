# WikiRenderer
WikiRenderer is a heavily modified version of [Isometric Renders](https://github.com/gliscowo/isometric-renders) that allows you to create renders of game objects like parts
of world, blocks, items and entities.

These are automatically keyed to have a transparent background, and you can adjust scale, positioning and many more
options right in-game in a menu.

Not only is this version of the mod also designed for modded wikis in mind, but it also has a couple of additional
features targeted for use on the [Hypixel SkyBlock Wiki](https://hypixelskyblock.minecraft.wiki).

Note: Starting with Minecraft 26.3, WikiRenderer embeds the UI system from [owo-lib](https://modrinth.com/mod/owo-lib) directly into the mod, needing it is no longer required as a separate dependency.

# Usage
WikiRenderer supports the following render types:

- Items
- Entities
    - Also supports sprite rendering
- Areas in the world
    - Also supports overhead images for minimaps
- Block States
- Item Tooltips
- Container Screens at various GUI scales
- Batch export of multiple blocks or items
    - Can pull from creative tabs, item namespaces, or your inventory
    - Can also render multiple items into an atlas
- Animated exports for items, entities, blocks states or world areas in various file formats (`.gif` using Gifski (built in), or `.apng`, `.webp`, `.mov`, or `.mp4` using FFmpeg (separate installation required))

The main command is `/wikirender`, but `/wr` also exists as an alias.

## Items
Items can be rendered in three ways:
1. Hold an item and type `/wikirender item`
2. Hover over an item in your inventory and press the render hotkey (defaults to `h`)
3. Type `/wikirender item id <item>`, where `<item>` is something like `minecraft:diamond`
4. Type `/wikirender item texture <texture>`, where `<texture>` is a texture ID or base64 of a texture ID for a player head

Below is an example of an enchanted compass made using `/wikirender item id minecraft:compass`:

<img src="src/main/resources/assets/wikirenderer/readme_images/enchanted_compass_menu.png" width="600" alt="Enchanted Compass Menu">

On the right is an option to speed up enchantment glint speeds. Normally, at 100% glint speed (seen in Minecraft's accessibility settings), a full loop of the glint animation takes 41.25 seconds, but this option speeds it up to 15 seconds, while barely being a noticeable difference.  The `Use Enchanted Items Timings` button then sets the animation timing settings to this duration.

<img src="src/main/resources/assets/wikirenderer/readme_images/enchanted_item_animation_options.png" width="300" alt="Enchantment Animation Options">

Player Heads can also be rendered, and you can also grab their texture data with included buttons. This was rendered with `/wr item texture d7cc6687423d0570d556ac53e0676cb563bbdd9717cd8269bdebed6f6d4e7bf8`.

<img src="src/main/resources/assets/wikirenderer/readme_images/player_head_item_render_menu.png" width="600" alt="Player Head Render Menu">

<img src="src/main/resources/assets/wikirenderer/readme_images/item_texture_grabbing.png" width="300" alt="Player Texture Copy Buttons">

## Areas

Areas in the world can be rendered in three ways:
1. Select two points in the world with the hotkey (defaults to `c`), and type `/wikirender area`.
2. Type `/wikirender area pos <start> <end>`, where `<start>` and `<end>` represent coordinates.
3. Type `/wikirender area island <chunk_size> <distance_limit>`, which scans outwards in mini chunks (specified by
   `<chunk_size>`), not continuing past empty chunks or until the distance limit is reached.
    - This is useful for rendering everything nearby you quickly, or for rendering a non-square-shaped island close to
      other islands. In the latter case, simply lower `<chunk_size>` until the surrounding islands are not hit in the
      scan.

Below is an example of the Hypixel Prototype lobby, rendered using `/wikirender area island 8 200`, featuring a variety of render options on both sides of the menu:

<img src="src/main/resources/assets/wikirenderer/readme_images/ptl_lobby_menu.png" width="600" alt="Hypixel Prototype Lobby">

Area renders are quite configurable. You can control block and entity visibility, override certain types of data on visible entities, modify how lighting is handled, and more.

### Minimap Renders
Area renders also feature topdown and side-view rendering modes, which allow you to create minimap images with adjustable texture resolution scaling. There is also a cave mode that lets you create underground images.

<img src="src/main/resources/assets/wikirenderer/readme_images/spiders_den_minimap_menu.png" width="600" alt="Hypixel SkyBlock Spider's Den Minimap Render">
<img src="src/main/resources/assets/wikirenderer/readme_images/lapis_quarry_cave_mode.png" width="600" alt="Hypixel SkyBlock Lapis Quarry Cave Minimap Render">

Minimap data can also be exported for use on the [Hypixel SkyBlock Wiki's Module:Minimap/Datasheet Minimap Calibrator tool](https://hypixelskyblock.minecraft.wiki/Module:Minimap/Datasheet).

### Item Frame Minimaps
On the topic of Minimaps, you can also use area rendering and side angle viewing to render pixel-perfect maps:

<img src="src/main/resources/assets/wikirenderer/readme_images/item_frame_map_render_area.png" height="256" alt="Item Frame Map">
<img src="src/main/resources/assets/wikirenderer/readme_images/item_frame_map_render.png" width="384" alt="Item Frame Map">

## Entities
Entities can be rendered in four ways:
1. Type `/wikirender player` to render yourself
2. Look at an entity and pressing the render hotkey (defaults to `h`)
3. Look at an entity and type `/wikirender entity`
4. Type `/wikirender entity <entity_type> <nbt>`, where `<entity_type>` is something like `minecraft:zombie`, and `<nbt>` is optional but something like `{IsBaby:1b}`

Below are examples of myself being rendered, both normally and in an optional sprite mode:

<img src="src/main/resources/assets/wikirenderer/readme_images/rendering_myself_menu.png" width="600" alt="Me wearing Diamond Armor and an Iron Helmet whilst holding a Golden Spear">
<img src="src/main/resources/assets/wikirenderer/readme_images/rendering_myself_sprite_menu.png" width="600" alt="A sprite of me wearing an iron helmet">

Entity renders have multiple options, such as the ability to use the live entity (useful for animated armor), freeze the arms, toggle item or enchantment visibility, and more.

Similar to player head item renders, you can also grab the texture data of rendered players, as well as player heads worn or held by rendered entities.

<img src="src/main/resources/assets/wikirenderer/readme_images/entity_texture_grabbing.png" width="600" alt="Options to copy texture data">

### Entity Lighting
Entities are lit in a way where in isometric viewing angles (`45°`, `135°`, `225°`, and `315°`), the top is 100% brightness, the left side is 80% brightness, and the right side is 60% brightness. Sprites are rendered at 100% brightness.

## Blocks
Individual blocks and block states can be rendered in three ways:
1. Look at a block and type `/wikirender block`
2. Type `/wikirender block <block>`, where `<block>` is something like `minecraft:cobblestone`
3. Type `/wikirender block <block>[data]`, as seen below

Below is an example of rendering a furnace using `/wikirender block minecraft:furnace[lit=true]{Items:[{Slot:0b, Count: 1b, id: "minecraft:coal"}]}`

<img src="src/main/resources/assets/wikirenderer/readme_images/furnace_block_state_render.png" width="300" alt="Furnace Block State Render Menu">

> Note: Waterlogged blocks currently do not render properly. For those, use a single-block area render.


## Item Tooltips
Item Tooltips can be rendered in similar ways to item renders:
1. Hold an item and type `/wikirender tooltip`
2. Hover over an item in your inventory and press the render tooltip hotkey (defaults to `k`)
3. Type `/wikirender tooltip <item>`, where `<item>` is something like `minecraft:diamond`

Tooltips are rendered using per-pixel resolution scaling, defaulting at 4 image pixels per font pixel.

<img src="src/main/resources/assets/wikirenderer/readme_images/plasmaflux_tooltip_render.png" width="600" alt="Plasmaflux Power Orb Tooltip Menu">

## Container Screens
Container Screens can be rendered by opening one up and pressing the keybind (defaults to `;`). You can then change your preview GUI scale, as well as the export GUI scale. The preview scale determines how much space you have to work with.

You can use the export keybind (`F12`) to render the image, allowing you to keep your mouse hovered over an item to show its tooltip.

<img src="src/main/resources/assets/wikirenderer/readme_images/creative_inventory_screen_render_menu.png" width="600" alt="Plasmaflux Power Orb Tooltip Menu">

<img src="src/main/resources/assets/wikirenderer/readme_images/creative_inventory_screen_render_example.png" width="500" alt="Plasmaflux Power Orb Tooltip Menu">


## Item-Based Batch Rendering
There are several ways to multiple render items, blocks, or tooltips at once:

### Inventories
To render every item in your inventory, press the associated hotkey (default `k`), which will open a popup allowing you to render the items, block item states, tooltips, or items in an atlas:

<img src="src/main/resources/assets/wikirenderer/readme_images/bazaar_batch_render_menu.png" width="600" alt="Bazaar Inventory Batch Render Popup Menu">

### Creative Tabs
To render a creative tab:
1. Type `/wikirender group item creative_tab <tab> itematlas` to render a certain tab as an atlas texture
2. Type `/wikirender group item creative_tab <tab> batch (blocks|items|tooltips)` to batch render a certain tab's items, blocks, or tooltips

Below is an example of every combat item rendered in an atlas using `/wikirender group item creative_tab minecraft:combat itematlas`:

<img src="src/main/resources/assets/wikirenderer/readme_images/creative_tab_atlas_render.png" width="600" alt="Creative Mode Atlas Render">

### Tagged Items
To render items, blocks or tooltips given a select tag:
1. Type `/wikirender group item tag <#namespace:tag> itematlas` to render the tag's items as an atlas texture
2. Type `/wikirender group item tag <#namespace:tag> batch (blocks|items|tooltips)` to batch render the tag's items, blocks, or tooltips

Below is an example of a batch render of every flower using `/wikirender group item tag #minecraft:flowers batch items`. The left side of the screen shows 30 items remaining, and they can all be rendered by pressing the start button.

<img src="src/main/resources/assets/wikirenderer/readme_images/flower_batch_render.png" width="600" alt="Flower Batch Render">

### Namespace Items
To render items, blocks or tooltips from a select namespace:
1. Type `/wikirender group item namespace <namespace> itematlas` to render the namespace's items as an atlas texture
2. Type `/wikirender group item namespace <namespace> batch (blocks|items|tooltips)` to batch render the namespace's items, blocks, or tooltips

Below is an example of an atlas render of every vanilla item using `/wikirender group item namespace minecraft itematlas`.

<img src="src/main/resources/assets/wikirenderer/readme_images/namespace_items_render.png" width="600" alt="Minecraft Items Batch Render">

## Entity-Based Batch Rendering
There are several ways to multiple entities at once:
### Tagged Entities
To render entities given a select tag:
1. Type `/wikirender group entity tag <#namespace:tag>` to render the tag's entities
2. Type `/wikirender group entity tag <#namespace:tag> nbt <nbt>` to render the tag's entities with NBT applied to each of them
3. Type `/wikirender group entity tag <#namespace:tag> nbt_filter <filter> <nbt>` to render the tag's entities with NBT applied to each of them, but only rendering those with pass the filter check (see below)

Below is an example of a batch render of every aquatic Minecraft mob using `/wikirender group entity tag #minecraft:aquatic`

<img src="src/main/resources/assets/wikirenderer/readme_images/aquatic_entity_batch_menu.png" width="600" alt="Minecraft Entities Batch Render">


### Namespace Entities
To render entities given a select namespace:
1. Type `/wikirender group entity namespace <namespace>` to render the namespace's entities
2. Type `/wikirender group entity namespace <namespace> nbt <nbt>` to render the namespace's entities with NBT applied to each of them
3. Type `/wikirender group entity namespace <namespace> nbt_filter <filter> <nbt>` to render the namespace's entities with NBT applied to each of them, but only rendering those with pass the filter check (see below)

Below is an example of a batch render of every Minecraft mob using `/wikirender group entity namespace minecraft`:

<img src="src/main/resources/assets/wikirenderer/readme_images/entity_batch_menu.png" width="600" alt="Minecraft Entities Batch Render">


### NBT Filtering:
When rendering entities from a namespace or tag, you can filter which entities from those lists actually get used by filtering by the applied NBT. There are three filter modes to choose from:
1. `require_all_valid`: Allows an entity to render if all the provided NBT tags are valid
2. `require_one_valid`: Allows an entity to render if at least one of the provided NBT tags is valid
3. `require_none_valid`: Allows an entity to render if none of the provided NBT tags are valid

More specifically, a "valid" tag is a tag that is saved with the entity when it tries to save its NBT. Some tags are exported for all entities, regardless of if they change how they render (i.e. `Equipment` for all living entities), whereas others only get saved for specific types (i.e. `Pumpkin` for snow golems)

For example, typing `/wikirender group entity namespace minecraft nbt_filter require_one_valid {Age:-1,IsBaby:1b}` will render all baby mobs, with some extra mobs included, as seen below:

<img src="src/main/resources/assets/wikirenderer/readme_images/baby_entity_batch_menu.png" width="600" alt="Minecraft Entities Batch Render">
