package com.skillup.apiPresentation;

import com.skillup.apiPresentation.dto.in.PromotionInDto;
import com.skillup.apiPresentation.dto.mapper.PromotionMapper;
import com.skillup.apiPresentation.dto.out.PromotionOutDto;
import com.skillup.apiPresentation.util.SkillUpCommon;
import com.skillup.domain.promotion.PromotionDomain;
import com.skillup.domain.promotion.PromotionService;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/promotion")
public class PromotionController {

  @Autowired PromotionService promotionService;

  @Autowired
  org.springframework.beans.factory.ObjectProvider<com.skillup.infrastructure.redis.PromotionCache>
      cache;

  @PostMapping("/preheat/id/{id}")
  public ResponseEntity<Boolean> preheat(@PathVariable("id") String id) {
    if (cache.getIfAvailable() == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(cache.getObject().preheat(id));
  }

  @PostMapping
  public PromotionOutDto createPromotion(@RequestBody PromotionInDto promotionInDto) {
    PromotionDomain domain = PromotionMapper.INSTANCE.toDomain(promotionInDto);
    domain.setPromotionId(UUID.randomUUID().toString());
    PromotionDomain saved = promotionService.createPromotion(domain);
    if (cache.getIfAvailable() != null) cache.getObject().preheat(saved.getPromotionId());
    return PromotionMapper.INSTANCE.toOutDto(saved);
  }

  @GetMapping("/id/{id}")
  public ResponseEntity<PromotionOutDto> getById(@PathVariable("id") String id) {
    PromotionDomain domain =
        cache.getIfAvailable() == null ? promotionService.getById(id) : cache.getObject().get(id);
    if (Objects.isNull(domain)) {
      return ResponseEntity.status(SkillUpCommon.BAD_REQUEST).body(null);
    }
    return ResponseEntity.status(SkillUpCommon.SUCCESS)
        .body(PromotionMapper.INSTANCE.toOutDto(domain));
  }

  @GetMapping("/status/{status}")
  public List<PromotionOutDto> getByStatus(@PathVariable("status") Integer status) {
    return promotionService.getByStatus(status).stream()
        .map(PromotionMapper.INSTANCE::toOutDto)
        .collect(Collectors.toList());
  }

  /** Reserve stock: available_stock -1, lock_stock +1 */
  @PostMapping("/lock/id/{id}")
  public ResponseEntity<Boolean> lockStock(@PathVariable("id") String id) {
    if (cache.getIfAvailable() != null) return ResponseEntity.status(409).body(false);
    return ResponseEntity.status(SkillUpCommon.SUCCESS).body(promotionService.lockStock(id));
  }

  /** Release stock: available_stock +1, lock_stock -1 */
  @PostMapping("/revert/id/{id}")
  public ResponseEntity<Boolean> revertStock(@PathVariable("id") String id) {
    if (cache.getIfAvailable() != null) return ResponseEntity.status(409).body(false);
    return ResponseEntity.status(SkillUpCommon.SUCCESS).body(promotionService.revertStock(id));
  }

  /** Consume reserved stock: lock_stock -1 */
  @PostMapping("/deduct/id/{id}")
  public ResponseEntity<Boolean> deductStock(@PathVariable("id") String id) {
    if (cache.getIfAvailable() != null) return ResponseEntity.status(409).body(false);
    return ResponseEntity.status(SkillUpCommon.SUCCESS).body(promotionService.deductStock(id));
  }
}
