# CraftKit zMenu Module Design

`craftkit-zmenu` is a lightweight integration module for plugins that use zMenu. It must reduce repeated integration boilerplate without replacing zMenu's public API, types, or configuration model.

This design is approved as the implementation contract for the module.

## Decision summary

| Area | Final decision |
| --- | --- |
| zMenu version | Use `fr.maxlego08.menu:zmenu-api:1.1.1.4`. |
| Gradle scope | Use `compileOnlyApi`, not `api` or plain `compileOnly`. |
| API philosophy | Expose real zMenu types such as `InventoryManager`, `ButtonManager`, `PatternManager`, `DialogManager`, and `BedrockManager`. |
| What CraftKit wraps | Only dependency/service resolution, bootstrap, default resource loading, recursive YAML loading, safe reload, and small open helpers with clear errors. |
| What CraftKit must not wrap | The full zMenu API, zMenu types, zMenu YAML syntax, button behavior, item component behavior, or every manager method. |
| Services | Required zMenu managers are resolved through Bukkit `ServicesManager`. `MenuPlugin` is resolved through Bukkit `PluginManager`. |
| Bootstrap | Explicitly configured by the consumer plugin. No classpath magic or automatic resource discovery. |
| Reload | Only promised for zMenu features that expose public unregister/delete APIs. Registrations without public unregister are enable-only. |
| SDD | This change is intentionally not using SDD. |

## Why this module exists

CraftKit should only exist where it prevents repeated bugs, unstable reloads, inconsistent dependency handling, or poor debugging. zMenu already provides a strong API; CraftKit must not replace it.

The repeated work that deserves standardization is:

- validating that zMenu is installed and enabled;
- resolving required and optional zMenu services consistently;
- failing with clear errors when services are missing;
- loading consumer plugin menu resources from predictable folders;
- copying declared default resources when needed;
- registering custom button loaders in the correct order;
- tracking reload-safe loaded resources;
- cleaning reload-safe resources before reloading;
- providing small `open` helpers that scope lookups to the consumer plugin and log clear failures.

## Module shape

```text
craftkit-zmenu
  com.hera.craftkit.zmenu
    ZMenus
    ZMenuIntegration
    ZMenuBootstrap
    ZMenuReloadPlan
    ZMenuException
    ZMenuMissingDependencyException
    ZMenuMissingServiceException

  com.hera.craftkit.zmenu.internal
    DefaultZMenuIntegration
    DefaultZMenuBootstrap
    DefaultZMenuReloadPlan
    ZMenuRegistrationTracker
    ZMenuFileLoader
    BukkitZMenuServices
```

The public package provides the small consumer-facing API. The `internal` package owns implementation details and must not be used by consumer plugins.

## Gradle configuration

Add the module:

```kotlin
include("craftkit-zmenu")
```

Add the version catalog entry:

```toml
[versions]
zmenu = "1.1.1.4"

[libraries]
zmenu-api = { module = "fr.maxlego08.menu:zmenu-api", version.ref = "zmenu" }
```

Use `compileOnlyApi` in `craftkit-zmenu`:

```kotlin
description = "zMenu integration helpers for CraftKit consumers."

dependencies {
    compileOnly(libs.paper.api)
    compileOnlyApi(libs.zmenu.api)
}
```

`compileOnlyApi` is required because `craftkit-zmenu` exposes zMenu API types in its own public API, while zMenu itself remains a server-provided plugin dependency and must not be bundled by CraftKit.

## Service resolution

zMenu registers managers through Bukkit `ServicesManager` during its `onEnable`.

Required services:

- `InventoryManager`
- `ButtonManager`
- `PatternManager`

Optional services:

- `DialogManager`, only available when zMenu loaded Dialog support;
- `BedrockManager`, only available when zMenu detected Geyser/Floodgate.

`MenuPlugin` is not resolved as a service. It must be resolved from Bukkit `PluginManager`:

```java
Plugin raw = plugin.getServer().getPluginManager().getPlugin("zMenu");
if (!(raw instanceof MenuPlugin menuPlugin)) {
    throw new ZMenuMissingDependencyException("zMenu is not loaded or does not expose MenuPlugin");
}
```

Service lookup should fail hard for required services:

```java
private static <T> T requireService(Plugin plugin, Class<T> type) {
    RegisteredServiceProvider<T> provider = plugin.getServer()
        .getServicesManager()
        .getRegistration(type);

    if (provider == null || provider.getProvider() == null) {
        throw new ZMenuMissingServiceException("zMenu service missing: " + type.getName());
    }

    return provider.getProvider();
}
```

Optional services must be returned as `Optional<T>`, not `null`.

## Public API

### Entry point

```java
ZMenuIntegration zmenu = ZMenus.require(plugin);
```

`ZMenus.require(plugin)` validates zMenu, resolves the required services, resolves optional services, and returns a ready integration object.

### Integration object

```java
public interface ZMenuIntegration {
    MenuPlugin menuPlugin();

    InventoryManager inventories();

    ButtonManager buttons();

    PatternManager patterns();

    Optional<DialogManager> dialogs();

    Optional<BedrockManager> bedrock();

    void open(Player player, String inventoryName);

    void open(Player player, String inventoryName, int page);

    void openWithHistory(Player player, String inventoryName, int page);

    ZMenuBootstrap bootstrap();

    ZMenuReloadPlan reloadPlan();

    void reload();
}
```

The manager accessors return real zMenu types. There must be no `CraftInventoryManager`, `CraftButtonManager`, or equivalent wrapper replacing zMenu's API.

## Bootstrap design

The bootstrap exists to make consumer plugin setup repeatable, ordered, and reload-aware.

Example consumer usage:

```java
private ZMenuIntegration zmenu;

@Override
public void onEnable() {
    this.zmenu = ZMenus.require(this);

    this.zmenu.bootstrap()
        .buttons(registry -> {
            registry.button(new NoneLoader(this, SearchButton.class, "hera_search"));
            registry.button(new MyCustomButtonLoader(this));
        })
        .buttonOptions(MyButtonOption.class)
        .inventoryOptions(MyInventoryOption.class)
        .defaultInventories(
            "inventories/main.yml",
            "inventories/profile.yml"
        )
        .defaultPatterns(
            "patterns/decoration.yml"
        )
        .actionPatterns("actions_patterns")
        .patterns("patterns")
        .inventories("inventories")
        .dialogs("dialogs")
        .bedrock("bedrock")
        .load();
}
```

### Ordered loading

The bootstrap must load resources in dependency order:

1. Register reload-safe custom loaders/options declared in bootstrap.
2. Copy/load declared default resources.
3. Load `action_patterns`.
4. Load `patterns`.
5. Load `inventories`.
6. Load `dialogs` if `DialogManager` exists.
7. Load `bedrock` inventories if `BedrockManager` exists.

This follows the practical pattern used by zAuctionHouse: custom button loaders and patterns must exist before inventories are loaded because inventory YAML files can reference them.

### Directory loading

Consumer plugin folders are not automatically loaded by zMenu. zMenu automatically loads its own folders under `plugins/zMenu`, but a consumer plugin must explicitly call zMenu manager methods for files under its own `dataFolder`.

CraftKit provides this reusable directory loading:

```text
plugins/MyPlugin/inventories/**/*.yml
plugins/MyPlugin/patterns/**/*.yml
plugins/MyPlugin/actions_patterns/**/*.yml
plugins/MyPlugin/dialogs/**/*.yml
plugins/MyPlugin/bedrock/**/*.yml
```

The implementation must:

- create configured folders when they do not exist;
- walk recursively;
- only load `.yml` files;
- call real zMenu manager methods;
- log the exact file that failed;
- continue or fail according to a clear module policy, preferably fail fast during startup and report full context.

### Default resources

Default resource copying must be explicit. CraftKit must not scan plugin JAR resources automatically.

Good:

```java
.defaultInventories("inventories/main.yml", "inventories/profile.yml")
.defaultPatterns("patterns/decoration.yml")
```

Avoid:

```java
.autoDiscoverResources()
```

For inventory defaults, prefer zMenu's own method when applicable:

```java
inventoryManager.loadInventoryOrSaveResource(plugin, "inventories/main.yml");
```

That method copies the resource from the consumer plugin JAR if the file does not exist, then loads it.

For resource categories where zMenu does not expose an equivalent `loadOrSaveResource` method, CraftKit may call:

```java
plugin.saveResource(path, false);
```

only for explicitly declared paths, then load the resulting file through the real zMenu manager.

Default resources must not overwrite existing files by default.

## Registration tracking

Tracking is only for objects CraftKit loads or registers through bootstrap.

zMenu still performs the real work:

```java
Inventory inventory = inventoryManager.loadInventory(plugin, file);
tracker.trackInventory(inventory);
```

Direct consumer calls are not tracked:

```java
zmenu.buttons().register(new MyButtonLoader(plugin));
```

Tracked resources:

- `ButtonLoader` registered through bootstrap;
- `Pattern` loaded or registered through bootstrap;
- `ActionPattern` loaded or registered through bootstrap;
- `Inventory` loaded through bootstrap;
- `DialogInventory` loaded through bootstrap;
- `BedrockInventory` loaded through bootstrap;
- `ButtonOption` classes registered through bootstrap;
- `InventoryOption` classes registered through bootstrap;
- `InventoryListener` instances registered through bootstrap;
- `FastEvent` instances registered through bootstrap.

The tracker exists only to support safe cleanup before reload.

## Reload design

Consumer plugin reload should be simple:

```java
public void reloadPlugin() {
    reloadConfig();
    this.zmenu.reload();
}
```

`zmenu.reload()` reloads the last loaded bootstrap plan.

Internally, reload must clean reload-safe resources first:

```text
1. unregister button loaders for this plugin
2. delete inventories loaded for this plugin
3. unregister button options for this plugin
4. unregister inventory options for this plugin
5. unregister fast event listener for this plugin
6. unregister tracked inventory listeners by instance
7. unregister tracked patterns by instance
8. unregister tracked action patterns by instance
9. delete dialogs for this plugin, if DialogManager exists
10. delete bedrock inventories for this plugin, if BedrockManager exists
11. clear CraftKit tracker
12. run the last bootstrap plan again
```

Relevant zMenu cleanup APIs:

```java
buttonManager.unregisters(plugin);
inventoryManager.deleteInventories(plugin);
inventoryManager.unregisterOptions(plugin);
inventoryManager.unregisterInventoryOptions(plugin);
inventoryManager.unregisterListener(plugin);
inventoryManager.unregisterInventoryListener(listener);
patternManager.unregisterPattern(pattern);
patternManager.unregisterActionPattern(actionPattern);
dialogManager.deleteDialog(plugin);
bedrockManager.deleteBedrockInventory(plugin);
```

Do not promise cleanup for APIs that zMenu does not expose publicly.

## Reload-safe vs enable-only

zMenu does not expose public unregister APIs for every extension point. CraftKit must not pretend those registrations are reload-safe.

### Reload-safe

| Feature | Cleanup API |
| --- | --- |
| Inventories | `InventoryManager.deleteInventories(plugin)` |
| Button loaders | `ButtonManager.unregisters(plugin)` |
| Patterns | `PatternManager.unregisterPattern(pattern)` |
| Action patterns | `PatternManager.unregisterActionPattern(pattern)` |
| Dialog inventories | `DialogManager.deleteDialog(plugin)` |
| Bedrock inventories | `BedrockManager.deleteBedrockInventory(plugin)` |
| Button options | `InventoryManager.unregisterOptions(plugin)` |
| Inventory options | `InventoryManager.unregisterInventoryOptions(plugin)` |
| Inventory listeners | `InventoryManager.unregisterInventoryListener(listener)` |
| Fast events | `InventoryManager.unregisterListener(plugin)` |
| zMenu commands, if supported later | `CommandManager.unregisterCommands(plugin)` |

### Enable-only

| Feature | Reason |
| --- | --- |
| Action loaders | No public `unregisterAction` API. |
| Permissible loaders | No public `unregisterPermissible` API. |
| Material loaders | No public unregister API. |
| ItemStack similar verifiers | No public unregister API. |
| Placeholders | No public unregister API. |
| Item component loaders | No public unregister API. |
| Title animation loaders | No public unregister API. |
| Custom item mechanic factories | No public unregister API. |
| Custom item mechanic listeners | No safe granular plugin-owned unregister API. |
| Config dialogs | No public unregister API. |

Enable-only registrations should happen once during `onEnable`, not during reload. If CraftKit exposes helpers for them, the API must clearly mark them as enable-only.

## Button registration model

Buttons are registered through zMenu's `ButtonManager`:

```java
buttonManager.register(new NoneLoader(plugin, MyButton.class, "myplugin_button"));
buttonManager.register(new MyButtonLoader(plugin));
```

The loader name is the YAML `type`:

```yaml
my-button:
  type: myplugin_button
  slot: 13
```

The loader name must be unique. zAuctionHouse uses explicit IDs such as:

```text
ZAUCTIONHOUSE_SEARCH
ZAUCTIONHOUSE_CATEGORY
ZAUCTIONHOUSE_SELL_PRICE
```

CraftKit should recommend stable, namespaced IDs for HERA plugins, for example:

```text
HERA_PROFILE_OPEN
HERA_SHOP_CATEGORY
HERA_CONFIRM_ACTION
```

or lowercase plugin-prefixed IDs if a plugin standard prefers that style. The important rule is uniqueness and consistency.

## Opening inventories

CraftKit may wrap opening because it adds real value: plugin-scoped lookup, clear errors, and optional history preservation.

Recommended behavior:

```java
zmenu.open(player, "main");
zmenu.open(player, "main", 2);
zmenu.openWithHistory(player, "main", 1);
```

Internally, lookup must be scoped to the consumer plugin:

```java
Optional<Inventory> inventory = inventoryManager.getInventory(plugin, inventoryName);
```

Avoid global lookup unless explicitly requested. zMenu's global `openInventory(player, inventoryName)` is less safe because inventory names can collide across plugins.

If an inventory is missing, CraftKit should log a clear error including:

- consumer plugin name;
- requested inventory name;
- known loaded inventories for that plugin if cheap to obtain;
- whether bootstrap has been loaded.

Opening inventories must happen on the correct server thread/context. If a consumer calls from async code, CraftKit should either document that the caller must switch context or provide a scheduler-aware helper only if it can do so without introducing a new lifecycle framework.

## Custom inventory, dialog, and bedrock classes

### Custom inventory class

zMenu supports custom inventory classes, but the constructor must match:

```java
(Plugin plugin, String name, String fileName, int size, List<Button> buttons)
```

If the constructor does not exist, zMenu falls back to its default `ZInventory` implementation.

CraftKit must not hide this requirement.

### Custom dialog and bedrock classes

The zMenu API exposes overloads that accept custom classes for dialogs and bedrock inventories, but the reviewed implementation currently does not effectively consume those classes in the loader.

CraftKit should use the normal methods:

```java
dialogManager.loadInventory(plugin, file);
bedrockManager.loadInventory(plugin, file);
```

Do not claim full custom class support for dialogs or bedrock in CraftKit's API.

## Commands

zMenu command loading is reload-safe because it exposes:

```java
commandManager.loadCommand(plugin, file);
commandManager.unregisterCommands(plugin);
```

However, command support is not part of the first implementation slice unless a consumer plugin immediately needs it. The first slice should stay focused on inventories, buttons, patterns, action patterns, dialogs, bedrock, options, listeners, service resolution, defaults, and reload.

## What must not be implemented

Do not implement:

- a full replacement API for zMenu;
- a CraftKit-specific inventory manager wrapper;
- a CraftKit-specific button manager wrapper;
- automatic classpath resource discovery;
- auto-discovery of consumer plugin folders without explicit configuration;
- a central CraftKit lifecycle manager;
- dependency injection;
- hidden global registries;
- reload promises for zMenu features without public unregister APIs;
- custom dialog/bedrock class support beyond what zMenu actually supports today.

## Implementation checklist

- [ ] Add `craftkit-zmenu` to Gradle settings.
- [ ] Add `zmenu = "1.1.1.4"` to the version catalog.
- [ ] Add `zmenu-api` library alias.
- [ ] Configure `craftkit-zmenu` with `compileOnly(libs.paper.api)` and `compileOnlyApi(libs.zmenu.api)`.
- [ ] Implement `ZMenus.require(plugin)`.
- [ ] Resolve `MenuPlugin` through `PluginManager`.
- [ ] Resolve required services through `ServicesManager` and fail clearly.
- [ ] Resolve optional `DialogManager` and `BedrockManager` as `Optional`.
- [ ] Expose real zMenu manager types from `ZMenuIntegration`.
- [ ] Implement explicit bootstrap configuration.
- [ ] Implement explicit default resource loading.
- [ ] Implement recursive `.yml` directory loading.
- [ ] Track only resources loaded or registered through bootstrap.
- [ ] Implement reload cleanup only for reload-safe resources.
- [ ] Mark non-unregisterable zMenu features as enable-only if helpers are added later.
- [ ] Add focused tests for service resolution, bootstrap planning, tracking, and reload cleanup where practical without requiring a live server.

## Final principle

`craftkit-zmenu` exists to make zMenu integration safe, consistent, and debuggable for HERA plugins. It must preserve the full power of zMenu instead of hiding it behind a weaker CraftKit abstraction.
