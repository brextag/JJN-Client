export default function Settings() {
  const sections = [
    { title: 'Video', items: ['Resolution', 'Fullscreen', 'VSync', 'Render Distance', 'Graphics Quality'] },
    { title: 'Audio', items: ['Master Volume', 'Music', 'Sound Effects', 'Ambient'] },
    { title: 'Controls', items: ['Keybinds', 'Mouse Sensitivity', 'Invert Mouse', 'Controller Support'] },
    { title: 'Interface', items: ['HUD Scale', 'Show FPS', 'Show Coordinates', 'Scoreboard'] },
    { title: 'Theme', items: ['Dark', 'Light', 'Accent Color', 'Blur Strength'] },
    { title: 'Language', items: ['English', 'Spanish', 'French', 'German', 'Japanese'] },
  ]

  return (
    <div className="h-full p-6 overflow-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="text-white/40 text-sm mt-1">Customize your experience</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {sections.map((section) => (
          <div key={section.title} className="glass-panel p-5">
            <h3 className="font-medium mb-4 text-jjn-300">{section.title}</h3>
            <div className="space-y-2">
              {section.items.map((item) => (
                <button
                  key={item}
                  className="w-full text-left px-3 py-2.5 rounded-lg text-sm text-white/70 hover:bg-white/5 hover:text-white transition-colors"
                >
                  {item}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
