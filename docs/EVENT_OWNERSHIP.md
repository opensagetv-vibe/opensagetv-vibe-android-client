# Android application event ownership

The Android client uses `VibeEventBus`, a synchronous typed dispatcher backed
by `OrderedListenerRegistry`. It replaces Square Otto without changing the
MiniClient protocol or player pipeline.

## Ordering and lifecycle contract

- A post runs synchronously on the posting thread, matching the former
  `ThreadEnforcer.ANY` bus behavior.
- Listeners run in registration order from a copy-on-write snapshot.
- Re-registering the same listener is idempotent.
- Unregistering is idempotent and affects the next post.
- `UIActivityLifeCycleHandler` registers at Activity resume and unregisters at
  pause. `VideoInfoDialog` registers only while shown.
- Unknown event types are logged and ignored. There is no reflective subscriber
  discovery and no production `DeadEvent` callback.

## Publisher/subscriber inventory

| Event | Publishers | Subscriber/owner |
| --- | --- | --- |
| `ConnectedEvent` | `MiniClient.publishConnectedIfCurrent` | No Android UI action; connection publication is already explicit |
| `ConnectionLost` | `MiniClientConnection` | `UIActivityLifeCycleHandler` |
| `DebugSageCommandEvent` | Core `EventRouter` | `UIActivityLifeCycleHandler` debug overlay |
| `ShowKeyboardEvent` | Core `EventRouter`, `NavigationDialog` | `UIActivityLifeCycleHandler` |
| `ShowNavigationEvent` | Core `EventRouter` | `UIActivityLifeCycleHandler` |
| `BackPressedEvent` | `DefaultKeyMap`, `NavigationDialog` | `UIActivityLifeCycleHandler` |
| `ChangePlayerOneTime` | `NavigationDialog` | `UIActivityLifeCycleHandler` |
| `CloseAppEvent` | `NavigationDialog` | `UIActivityLifeCycleHandler` |
| `HideKeyboardEvent` | Android UI callers | `UIActivityLifeCycleHandler` |
| `HideNavigationEvent` | `NavigationDialog` | `UIActivityLifeCycleHandler` |
| `HideSystemUIEvent` | `NavigationDialog` | `UIActivityLifeCycleHandler` |
| `MessageEvent` | `AppUtil.message` | `UIActivityLifeCycleHandler` main-thread message display |
| `ToggleAspectRatioEvent` | `NavigationDialog`, `VideoInfoDialog` | `UIActivityLifeCycleHandler` |
| `DebugKeyEvent` | `KeyMapProcessor` | `UIActivityLifeCycleHandler` debug overlay |
| `VideoInfoShow` | player/UI actions | `UIActivityLifeCycleHandler` |
| `VideoInfoRefresh` | active player progress | `VideoInfoDialog` while shown |

`ServersActivity` previously registered despite having no subscriber method;
that inert registration was removed.

Core `DeadBus` and the desktop Guava event implementation are separate fallback
and desktop concerns. This Android migration does not change either one.
