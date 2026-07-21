package ca.admin.delivermore.views.utility;

import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ca.admin.delivermore.data.service.MenuImageAssetService;

@RestController
@RequestMapping("/api/menu-images")
public class MenuImageApiController {

    private final MenuImageAssetService menuImageAssetService;

    public MenuImageApiController(MenuImageAssetService menuImageAssetService) {
        this.menuImageAssetService = menuImageAssetService;
    }

    @GetMapping("/{assetId}")
    public ResponseEntity<?> getImage(@PathVariable("assetId") Long assetId) {
        return menuImageAssetService.resolveImageResource(assetId)
                .map(resolved -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic())
                        .contentType(MediaType.parseMediaType(resolved.asset().getContentType()))
                        .body(resolved.resource()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}