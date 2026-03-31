package com.budget.entity;

import com.budget.model.TypeFacture;

import jakarta.persistence.*;

/**
 * Table d'apprentissage local des fournisseurs.
 * Chaque entree associe un mot-cle (trouve dans le texte d'une facture)
 * a un nom de fournisseur et un type de facture.
 *
 * Exemple : motCle="proximus" -> fournisseur="Proximus", type=INTERNET
 *
 * Cette table est alimentee automatiquement quand l'utilisateur corrige
 * ou confirme un fournisseur lors de l'import d'une facture.
 */
@Entity
@Table(name = "fournisseur_mapping")
public class FournisseurMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Le mot-cle en minuscules qu'on cherche dans le texte de la facture
    @Column(nullable = false, unique = true)
    private String motCle;

    // Le nom "propre" du fournisseur a afficher
    @Column(nullable = false)
    private String fournisseur;

    // Le type de facture associe (ex: INTERNET, ELECTRICITE...)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeFacture type;

    public FournisseurMapping() {
    }

    public FournisseurMapping(String motCle, String fournisseur, TypeFacture type) {
        this.motCle = motCle;
        this.fournisseur = fournisseur;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMotCle() {
        return motCle;
    }

    public void setMotCle(String motCle) {
        this.motCle = motCle;
    }

    public String getFournisseur() {
        return fournisseur;
    }

    public void setFournisseur(String fournisseur) {
        this.fournisseur = fournisseur;
    }

    public TypeFacture getType() {
        return type;
    }

    public void setType(TypeFacture type) {
        this.type = type;
    }
}
