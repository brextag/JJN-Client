export default function Cosmetics() {
  const categories = ['Capes', 'Wings', 'Hats', 'Accessories']

  return (
    <div className="h-full p-6 overflow-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold">Cosmetics</h1>
        <p className="text-white/40 text-sm mt-1">Customize your character appearance</p>
      </div>

      <div className="flex gap-6">
        {/* Preview */}
        <div className="w-80 glass-panel flex flex-col items-center justify-center p-8 sticky top-0">
          <div className="w-40 h-56 rounded-2xl bg-gradient-to-b from-white/10 to-white/5 border border-white/10 flex items-center justify-center mb-4">
            <div className="text-center text-white/30 text-sm">
              Live Skin
              <br />
              Preview
            </div>
          </div>
          <div className="text-sm font-medium">Current Look</div>
        </div>

        {/* Categories */}
        <div className="flex-1 space-y-8">
          {categories.map((cat) => (
            <div key={cat}>
              <h3 className="font-medium mb-3 text-white/70">{cat}</h3>
              <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-5 gap-3">
                {Array.from({ length: 5 }).map((_, i) => (
                  <button
                    key={i}
                    className="aspect-square rounded-xl bg-white/5 hover:bg-white/10 border border-white/5 hover:border-jjn-500/40 transition-all flex items-center justify-center text-white/20 text-xs"
                  >
                    {cat.slice(0, 1)}{i + 1}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
