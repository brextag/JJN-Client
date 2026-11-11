import { Activity, Cpu, HardDrive, MemoryStick } from 'lucide-react'

export default function Performance() {
  return (
    <div className="h-full p-6 overflow-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold">Performance</h1>
        <p className="text-white/40 text-sm mt-1">Real-time metrics and optimization</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4 mb-6">
        {[
          { label: 'FPS', value: '287', icon: Activity, color: 'text-green-400' },
          { label: 'Memory', value: '2.4 GB', icon: MemoryStick, color: 'text-blue-400' },
          { label: 'RAM Allocated', value: '4 GB', icon: HardDrive, color: 'text-purple-400' },
          { label: 'CPU', value: '18%', icon: Cpu, color: 'text-orange-400' },
        ].map((stat) => (
          <div key={stat.label} className="glass-panel p-5">
            <div className="flex items-center gap-2 mb-3">
              <stat.icon size={16} className={stat.color} />
              <span className="text-sm text-white/50">{stat.label}</span>
            </div>
            <div className="text-3xl font-semibold tracking-tight">{stat.value}</div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="glass-panel p-6">
          <h3 className="font-medium mb-4">Graphics Presets</h3>
          <div className="space-y-2">
            {['Ultra Performance', 'Balanced', 'Quality', 'Custom'].map((preset, i) => (
              <button
                key={preset}
                className={`w-full text-left px-4 py-3 rounded-xl transition-colors text-sm ${
                  i === 1 ? 'bg-jjn-500/20 text-jjn-300 ring-1 ring-jjn-500/40' : 'bg-white/5 hover:bg-white/10'
                }`}
              >
                {preset}
              </button>
            ))}
          </div>
        </div>

        <div className="glass-panel p-6">
          <h3 className="font-medium mb-4">FPS Limit</h3>
          <div className="space-y-4">
            <div className="flex items-center justify-between text-sm">
              <span className="text-white/50">Unlimited</span>
              <span className="font-medium">∞</span>
            </div>
            <input
              type="range"
              min="30"
              max="1000"
              defaultValue="0"
              className="w-full accent-jjn-500"
            />
            <div className="flex gap-2">
              {[60, 120, 144, 240, 360].map((fps) => (
                <button
                  key={fps}
                  className="flex-1 py-2 rounded-lg bg-white/5 hover:bg-white/10 text-xs transition-colors"
                >
                  {fps}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
