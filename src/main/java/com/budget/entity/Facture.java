package com.budget.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.budget.model.TypeFacture;

/**
 * Représente une facture liée à un mois spécifique.
 */
@Entity
@Table(name = "facture")
public class Facture {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "mois_budget_id")
    private MoisBudget moisBudget;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeFacture type;
    
    @Column(nullable = false, length = 100)
    private String fournisseur;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montant;
    
    @Column(nullable = false)
    private LocalDate dateFacture;  // Date de la facture (jour/mois/année)
                                    // Par défaut: date de saisie
                                    // Peut être modifiée par l'utilisateur
    
    @Column(nullable = false)
    private LocalDateTime dateCreation;  // Date/heure de création de l'enregistrement
    
    @Column(nullable = false)
    private LocalDateTime dateModification;  // Date/heure de dernière modification

    // Chemin relatif vers le document source (PDF/image) stocke localement.
    // Peut etre null si la facture a ete saisie manuellement.
    @Column(length = 500)
    private String documentPath;

    // Constructeurs
    public Facture() {
        this.dateCreation = LocalDateTime.now();
        this.dateModification = LocalDateTime.now();
    }

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public MoisBudget getMoisBudget() {
        return moisBudget;
    }

    public void setMoisBudget(MoisBudget moisBudget) {
        this.moisBudget = moisBudget;
    }

    public TypeFacture getType() {
        return type;
    }

    public void setType(TypeFacture type) {
        this.type = type;
    }

    public String getFournisseur() {
        return fournisseur;
    }

    public void setFournisseur(String fournisseur) {
        this.fournisseur = fournisseur;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public void setMontant(BigDecimal montant) {
        this.montant = montant;
    }

    public LocalDate getDateFacture() {
        return dateFacture;
    }

    public void setDateFacture(LocalDate dateFacture) {
        this.dateFacture = dateFacture;
    }

    public LocalDateTime getDateCreation() {
        return dateCreation;
    }

    public void setDateCreation(LocalDateTime dateCreation) {
        this.dateCreation = dateCreation;
    }

    public LocalDateTime getDateModification() {
        return dateModification;
    }

    public void setDateModification(LocalDateTime dateModification) {
        this.dateModification = dateModification;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    public boolean hasDocument() {
        return documentPath != null && !documentPath.isEmpty();
    }

    /**
     * Retourne la catégorie de budget associée à cette facture.
     */
    public com.budget.model.CategorieBudget getCategorie() {
        return type.getCategorie();
    }

    /**
     * Retourne l'ID sous forme de String (pour compatibilité avec l'ancien modèle).
     */
    public String getIdString() {
        return id != null ? id.toString() : null;
    }
}
