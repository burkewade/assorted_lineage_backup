# Jelly Browser

* Images disabled
* Javascript disabled

Jelly browser with ads blocker, support for android 6.0+, a few ui changes and some bug fixes.
Ads blocker and favicon in search bar based on this: https://github.com/CarbonROM/android_packages_apps_Quarks

### Ads blocker:
 * https://pgl.yoyo.org/as/serverlist.php?hostformat=nohtml&showintro=0

### Offline reading:
 * .mht (chromiumPC compatible)
 * /Android/data/com.oF2pks.jquarks/files/*.mht
 * ✇Favorites
 * screen Shortcuts
 
### tab(s) manager:
 * long-click on 3-dots main screen
 * tile & iconShortcut for allTabs kill
 
### external launches:
 * local xml/mht/html/svg/eml, for both ^content^ (X-plore) & ^file^ (aosp/Files or GhostCommander)
 * local video (with screen-off audio)
 * ShareLink
 * ShareContent
 * web search

### More Search-engine(s):
chosen one (via /Settings/) triggered, from any selected text (anywhere via longpress)
 * Gibiru
 * Mojeek
 * Qwant
 * SearX
 * StartPage
 * Swisscows

## AOSP compilation: ***packages/apps/Quarks/***
```
use branch -b jQuarksMore (org.lineageos.jelly)
```

```
etc/sysconfig/?.xml 
```
>^__hidden-api-whitelisted-app package="org.lineageos.jelly"/__^

prim-origin: https://github.com/LineageOS/android_packages_apps_Jelly
