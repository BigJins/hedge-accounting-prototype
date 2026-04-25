package com.hedge.prototype.hedge.application.port;

import com.hedge.prototype.hedge.domain.model.HedgedItem;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 위험회피대상항목 Repository.
 *
 * @see K-IFRS 1109호 6.4.1(2) (위험회피대상항목 식별·문서화 의무)
 */
public interface HedgedItemRepository extends JpaRepository<HedgedItem, String> {
}
