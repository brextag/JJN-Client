import { motion } from 'framer-motion'
import { Play, ChevronDown, Server, Newspaper } from 'lucide-react'

export default function Home() {
  return (
    <div className="h-full flex p-6 gap-6">
      {/* Left - Character Preview */}
      <div className="w-[380px] glass-panel flex flex-col items-center justify-center relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-b from-jjn-500/10 to-transparent pointer-events-none" />

        {/* Placeholder 3D character */}
        <div className="relative z-10 flex flex-col items-center">
          <div className="w-48 h-64 rounded-2xl bg-gradient-to-b from-white/10 to-white/5 border border-white/10 flex items-center justify-center mb-6 animate-float">
            <div className="text-center">
              <div className="w-24 h-24 mx-auto mb-3 rounded-full bg-gradient-to-br from-jjn-400 to-jjn-700 shadow-xl shadow-jjn-500/40" />
              <div className="text-xs text-white/40">3D Skin Preview</div>
              <div className="text-[10px] text-white/30 mt-1">Drag to rotate</div>
            </div>
          </div>

          <div className="text-center">
            <div className="text-lg font-semibold">Player</div>
            <div className="text-sm text-white/40">Steve</div>
          </div>
        </div>
      </div>

      {/* Right content */}
      <div className="flex-1 flex flex-col gap-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-semibold tracking-tight">Welcome back</h1>
            <p className="text-white/40 mt-1">Ready to play?</p>
          </div>

          <div className="flex items-center gap-3">
            <button className="glass-button px-4 py-2 rounded-xl text-sm flex items-center gap-2">
              1.21.3
              <ChevronDown size={16} className="opacity-60" />
            </button>
          </div>
        </div>

        {/* Play section */}
        <div className="glass-panel p-8 flex items-center justify-between">
          <div>
            <div className="text-sm text-white/50 mb-1">Minecraft Java Edition</div>
            <div className="text-2xl font-semibold">1.21.3</div>
            <div className="text-sm text-white/40 mt-1">Latest release</div>
          </div>

          <motion.button
            whileHover={{ scale: 1.03 }}
            whileTap={{ scale: 0.98 }}
            className="play-button px-12 py-4 rounded-2xl font-semibold text-lg flex items-center gap-3 text-white"
          >
            <Play size={22} fill="currentColor" />
            Play
          </motion.button>
        </div>

        {/* Bottom grid */}
        <div className="grid grid-cols-2 gap-6 flex-1">
          {/* Servers */}
          <div className="glass-panel p-6 flex flex-col">
            <div className="flex items-center gap-2 mb-4">
              <Server size={18} className="text-jjn-400" />
              <h3 className="font-medium">Quick Join</h3>
            </div>
            <div className="space-y-2 flex-1">
              {['Hypixel', 'Minehut', 'CubeCraft'].map((server) => (
                <button
                  key={server}
                  className="w-full text-left px-4 py-3 rounded-xl bg-white/5 hover:bg-white/10 transition-colors text-sm flex items-center justify-between group"
                >
                  <span>{server}</span>
                  <span className="text-xs text-white/30 group-hover:text-white/50">Join →</span>
                </button>
              ))}
            </div>
          </div>

          {/* News */}
          <div className="glass-panel p-6 flex flex-col">
            <div className="flex items-center gap-2 mb-4">
              <Newspaper size={18} className="text-jjn-400" />
              <h3 className="font-medium">News & Updates</h3>
            </div>
            <div className="space-y-3 flex-1 overflow-auto">
              <div className="p-3 rounded-xl bg-white/5">
                <div className="text-sm font-medium">JJN Client v1.0 Foundation</div>
                <div className="text-xs text-white/40 mt-1">Initial release of the premium launcher and mod framework.</div>
              </div>
              <div className="p-3 rounded-xl bg-white/5">
                <div className="text-sm font-medium">Liquid Glass UI</div>
                <div className="text-xs text-white/40 mt-1">New translucent design language inspired by modern systems.</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
