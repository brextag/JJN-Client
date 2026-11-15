package com.jjn.client.mixin;

import com.jjn.client.ui.JjnCosmetics;
import net.minecraft.resource.LifecycledResourceManagerImpl;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

@Mixin(LifecycledResourceManagerImpl.class)
public abstract class LifecycledResourceManagerImplMixin {

    @Shadow
    @Final
    private List<ResourcePack> packs;

    @Unique
    private static final List<String> JJN_BUNDLED_PATHS = List.of(
            "font/icons.json",
            "textures/font/jjn_icon.png",
            "textures/gui/jjn_icon.png"
    );

    @Unique
    private Resource jjnclient$createClasspathResource(Identifier id) {
        if (id == null || !"jjnclient".equals(id.getNamespace())) {
            return null;
        }
        String cpPath = "/assets/jjnclient/" + id.getPath();
        if (JjnCosmetics.class.getResource(cpPath) == null) {
            return null;
        }
        ResourcePack owner = this.packs.isEmpty() ? null : this.packs.getFirst();
        return new Resource(owner, () -> JjnCosmetics.class.getResourceAsStream(cpPath));
    }

    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void jjnclient$getResource(Identifier id, CallbackInfoReturnable<Optional<Resource>> cir) {
        Resource res = jjnclient$createClasspathResource(id);
        if (res != null) {
            cir.setReturnValue(Optional.of(res));
        }
    }

    @Inject(method = "getAllResources", at = @At("HEAD"), cancellable = true)
    private void jjnclient$getAllResources(Identifier id, CallbackInfoReturnable<List<Resource>> cir) {
        Resource res = jjnclient$createClasspathResource(id);
        if (res != null) {
            cir.setReturnValue(List.of(res));
        }
    }

    @Inject(method = "findResources", at = @At("RETURN"), cancellable = true)
    private void jjnclient$findResources(
            String prefix,
            Predicate<Identifier> filter,
            CallbackInfoReturnable<Map<Identifier, Resource>> cir
    ) {
        String norm = prefix == null || prefix.isEmpty() ? "" : (prefix.endsWith("/") ? prefix : prefix + "/");
        Map<Identifier, Resource> out = null;
        for (String path : JJN_BUNDLED_PATHS) {
            if (norm.isEmpty() || path.startsWith(norm)) {
                Identifier id = Identifier.of("jjnclient", path);
                if (filter.test(id)) {
                    Resource res = jjnclient$createClasspathResource(id);
                    if (res != null) {
                        if (out == null) {
                            out = new HashMap<>(cir.getReturnValue());
                        }
                        out.putIfAbsent(id, res);
                    }
                }
            }
        }
        if (out != null) {
            cir.setReturnValue(out);
        }
    }

    @Inject(method = "findAllResources", at = @At("RETURN"), cancellable = true)
    private void jjnclient$findAllResources(
            String prefix,
            Predicate<Identifier> filter,
            CallbackInfoReturnable<Map<Identifier, List<Resource>>> cir
    ) {
        String norm = prefix == null || prefix.isEmpty() ? "" : (prefix.endsWith("/") ? prefix : prefix + "/");
        Map<Identifier, List<Resource>> out = null;
        for (String path : JJN_BUNDLED_PATHS) {
            if (norm.isEmpty() || path.startsWith(norm)) {
                Identifier id = Identifier.of("jjnclient", path);
                if (filter.test(id)) {
                    Resource res = jjnclient$createClasspathResource(id);
                    if (res != null) {
                        if (out == null) {
                            out = new HashMap<>(cir.getReturnValue());
                        }
                        out.putIfAbsent(id, List.of(res));
                    }
                }
            }
        }
        if (out != null) {
            cir.setReturnValue(out);
        }
    }
}
