package com.budget.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.budget.entity.FournisseurMapping;

/**
 * Repository pour acceder a la table fournisseur_mapping.
 * Spring Data JPA genere automatiquement l'implementation des methodes
 * a partir de leur nom (convention "findBy...").
 */
@Repository
public interface FournisseurMappingRepository extends JpaRepository<FournisseurMapping, Long> {

    // Cherche une entree par son mot-cle exact
    Optional<FournisseurMapping> findByMotCle(String motCle);

    // Retourne toutes les entrees (utile pour scanner le texte)
    List<FournisseurMapping> findAll();
}
