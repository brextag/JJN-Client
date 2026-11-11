import { useState } from 'react'
import { clsx } from 'clsx'

const modList = [
  { id: 'toggle-sprint', name: 'Toggle Sprint', desc: 'Keep sprinting without holding the key', category: 'Movement' },
  { id: 'motion-blur', name: 'Motion Blur', desc: 'Subtle cinematic motion blur effect', category: 'Visual' },
  { id: 'zoom', name: 'Zoom', desc: 'Smooth optical zoom with configurable FOV', category: 'Utility' },
  { id: 'freelook', name: 'Freelook', desc: 'Look around freely without changing direction', category: 'Utility' },
  { id: 'keystrokes', name: 'Keystrokes', desc: 'On-screen key display for content creators', category: 'HUD' },
  { id: 'cps', name: 'CPS Counter', desc: 'Left and right click per second counter', category: 'HUD' },
  { id: 'fps', name: 'FPS Display', desc: 'Real-time frames per second counter', category: 'HUD' },
  { id: 'armor-hud', name: 'Armor HUD', desc: 'Display current armor durability', category: 'HUD' },
  { id: 'direction', name: 'Direction HUD', desc: 'Show facing direction (N/S/E/W)', category: 'HUD' },
  { id: 'coordinates', name: 'Coordinates', desc: 'XYZ position display', category: 'HUD' },
  { id: 'ping', name: 'Ping Display', desc: 'Server latency indicator', category: 'HUD' },
  { id: 'crosshair', name: 'Custom Crosshair', desc: 'Fully customizable crosshair editor', category: 'Visual' },
  { id: 'hit-color', name: 'Hit Color', desc: 'Change entity hit color', category: 'Visual' },
  { id: 'item-physics', name: 'Item Physics', desc: 'Realistic item drop physics', category: 'Visual' },
]

export default function Mods() {
  const [enabled, setEnabled] = useState<Record<string, boolean>>({
    'toggle-sprint': true,
    'fps': true,
    'coordinates': true,
    'ping': true,
  })

  const toggle = (id: string) => {
    setEnabled((prev) => ({ ...prev, [id]: !prev[id] }))
  }

  return (
    <div className="h-full p-6 overflow-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold">Mods</h1>
        <p className="text-white/40 text-sm mt-1">Enable and configure individual modules</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
        {modList.map((mod) => {
          const isOn = !!enabled[mod.id]
          return (
            <div
              key={mod.id}
              className={clsx(
                'glass-panel p-5 transition-all duration-200',
                isOn && 'ring-1 ring-jjn-500/40'
              )}
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="font-medium">{mod.name}</div>
                  <div className="text-xs text-white/40 mt-1">{mod.desc}</div>
                  <div className="text-[10px] uppercase tracking-wider text-white/25 mt-2">{mod.category}</div>
                </div>

                <button
                  onClick={() => toggle(mod.id)}
                  className={clsx(
                    'relative w-11 h-6 rounded-full transition-colors duration-200 shrink-0',
                    isOn ? 'bg-jjn-500' : 'bg-white/10'
                  )}
                >
                  <span
                    className={clsx(
                      'absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform duration-200',
                      isOn && 'translate-x-5'
                    )}
                  />
                </button>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
