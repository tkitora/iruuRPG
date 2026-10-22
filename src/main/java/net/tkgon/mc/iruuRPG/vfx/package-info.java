/**
 * The toolkit for the plugin's particle effects.
 *
 * <ul>
 *   <li>{@link net.tkgon.mc.iruuRPG.vfx.Gradient}: colors worked out between stops in RGB (a baked table of 256, shared
 *       {@code DustOptions}); {@link net.tkgon.mc.iruuRPG.vfx.Palettes}: the gradients of the elements, of purple and steel.</li>
 *   <li>{@link net.tkgon.mc.iruuRPG.vfx.Curves}: exponential / logarithmic / smoothstep curves for the look of a fade or a burst;
 *       {@link net.tkgon.mc.iruuRPG.vfx.Trig}: a sine table.</li>
 *   <li>{@link net.tkgon.mc.iruuRPG.vfx.Shapes}: baked unit circles and spheres (made once, scaled and turned by the effect).</li>
 *   <li>{@link net.tkgon.mc.iruuRPG.vfx.Fx}: draws lines, rings, spirals (logarithmic), spheres and discs with a gradient, with
 *       distance culling; {@link net.tkgon.mc.iruuRPG.vfx.FxAsync}: works the points of a big effect out off the main thread.</li>
 *   <li>{@link net.tkgon.mc.iruuRPG.vfx.flow.FlowEffects}: light fluid-like flows (turbulence, vortex, ripple, orbit, fountain) of a few hundred
 *       particles moved by a cheap vector field; try them with {@code /vfx list | help | test}. Not for combos yet (see {@code vfx.flow.FlowRunner}).</li>
 * </ul>
 *
 * <p>To make a new effect: pick the shape template, give it a gradient (never one color for the whole effect) and
 * make an {@code Fx} once for the spot the effect is at; inside a loop use that {@code Fx} and its {@code step()}.
 */
package net.tkgon.mc.iruuRPG.vfx;
