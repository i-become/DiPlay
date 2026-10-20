# CarPlay HS5 — Design system (redesign exploration)

## Branding (mandatory in all UI mockups)
- Header / title chrome: **CarPlay** + **HS5** — never **DiPlay** in driver-facing design artifacts.
- Package name may remain DiPlay in code; design previews and copy use **CarPlay**.

## Product
- Hongqi HS5 head unit, Android 9, **1920×720 landscape only**
- Jetpack Compose app today; HTML prototypes must match future Compose implementation density
- Language: Simplified Chinese primary labels (English OK for CarPlay brand terms)

## Canvas
- Viewport: **1920×720 px**, no vertical phone scroll
- Minimum touch target: **48px** height; primary actions **64–88px**
- Safe margins: 24–32px outer; sidebar ~200–240px

## Existing navigation (must remain functionally)
Sidebar destinations: 连接 · 显示 · 设置 · 诊断 · 关于  
Top bar: HS5/CarPlay branding, wireless/session status hints

## Connection page — required controls (no new features)
1. **Status hero**: phase (未配置 / 就绪 / 连接中 / 已连接 / 失败), selected iPhone name, optional link line (方式 + IP)
2. **连接方式** entry (Wi‑Fi Direct / 车载热点 / 现有 Wi‑Fi / 本地热点)
3. **进入 CarPlay** (primary)
4. **切换/连接 iPhone**
5. **断开连接** (enabled when session running)
6. **向 iPhone 报告位置** toggle + title/subtitle

## Brand (Hongqi)
- Deep charcoal / blue-black base `#071426` family
- Accent red for Hongqi direction: ~`#C8102E` (use sparingly)
- Avoid neon, heavy gradients, admin-dashboard chrome

## Typography
- System UI stack: `-apple-system, "SF Pro Display", "PingFang SC", "Helvetica Neue", sans-serif`
- Title 28–36px semibold; body 18–22px; captions 14–16px

## Motion (prototype)
- 180–320ms ease for hover/active on buttons, toggles, tabs
- Subtle opacity/translate on state chips (连接中 pulsing dot)
