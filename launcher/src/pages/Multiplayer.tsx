import { Plus, Search } from 'lucide-react'

export default function Multiplayer() {
  return (
    <div className="h-full p-6 overflow-auto">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold">Multiplayer</h1>
          <p className="text-white/40 text-sm mt-1">Join servers or manage your list</p>
        </div>
        <button className="glass-button px-4 py-2 rounded-xl text-sm flex items-center gap-2">
          <Plus size={16} />
          Add Server
        </button>
      </div>

      <div className="relative mb-6">
        <Search size={16} className="absolute left-4 top-1/2 -translate-y-1/2 text-white/30" />
        <input
          type="text"
          placeholder="Search servers..."
          className="w-full glass-panel pl-11 pr-4 py-3 rounded-xl bg-transparent outline-none text-sm placeholder:text-white/30"
        />
      </div>

      <div className="space-y-3">
        {[
          { name: 'Hypixel Network', motd: 'A Minecraft Server', players: '48,291 / 200,000', ping: 34 },
          { name: 'Minehut', motd: 'Free Minecraft Servers', players: '12,104 / ∞', ping: 52 },
          { name: 'CubeCraft Games', motd: 'Mini-games & more', players: '8,442 / 20,000', ping: 67 },
        ].map((server) => (
          <div
            key={server.name}
            className="glass-panel p-4 flex items-center gap-4 hover:bg-white/[0.07] transition-colors cursor-pointer"
          >
            <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-jjn-500/30 to-purple-600/30 flex items-center justify-center text-lg font-bold">
              {server.name[0]}
            </div>
            <div className="flex-1 min-w-0">
              <div className="font-medium truncate">{server.name}</div>
              <div className="text-xs text-white/40 truncate">{server.motd}</div>
            </div>
            <div className="text-right text-sm">
              <div className="text-white/70">{server.players}</div>
              <div className="text-xs text-green-400">{server.ping} ms</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
