package com.saee.addecision.repository;

import com.saee.addecision.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    List<Campaign> findByActiveTrue();

    @Modifying
    @Query("update Campaign c set c.impressions = c.impressions + 1 where c.id = :id")
    int incrementImpressions(@Param("id") long id);

    @Modifying
    @Query("update Campaign c set c.clicks = c.clicks + 1, c.spentCents = c.spentCents + :cost where c.id = :id")
    int recordClick(@Param("id") long id, @Param("cost") long costCents);
}
