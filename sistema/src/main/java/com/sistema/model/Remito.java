package com.sistema.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
    @Table(name = "remito", uniqueConstraints =
            @UniqueConstraint(name = "uk_remito_tenant_codigo", columnNames = {"tenant_id", "codigo"}))
    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public class Remito extends TenantAwareEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "codigo", nullable = false, length = 50)
        private String codigo;

        @ManyToOne
        @JoinColumn(name = "cliente_id")
        private Cliente cliente;

        @Column(name = "fecha_emision", nullable = false)
        private LocalDate fechaEmision;

        @Column(name = "direccion_entrega", length = 255)
        private String direccionEntrega;

        @Column(name = "observaciones", length = 500)
        private String observaciones;

        @Enumerated(EnumType.STRING)
        @Column(name = "estado", nullable = false, length = 20)
        private Estado estado = Estado.PENDIENTE;

        @Enumerated(EnumType.STRING)
        @Column(name = "tipo", nullable = false, length = 20)
        private Tipo tipo = Tipo.ENTREGA;

        @OneToMany(mappedBy = "remito", cascade = CascadeType.ALL, orphanRemoval = true)
        private List<RemitoItem> items = new ArrayList<>();

        @ManyToOne
        @JoinColumn(name = "venta_id")
        private Venta venta;

        @Column(name = "fecha_conversion")
        private LocalDate fechaConversion;

        @Column(name = "total", precision = 10, scale = 2)
        private BigDecimal total = BigDecimal.ZERO;

        @Column(name = "incluye_precios", nullable = false)
        private Boolean incluyePrecios = false; // Si muestra precios o no

        // ==========================================
        // ENUMS
        // ==========================================

        public enum Estado {
            PENDIENTE,      // Creado pero no entregado
            ENTREGADO,      // Mercadería entregada
            CONVERTIDO,     // Convertido a venta/factura
            ANULADO         // Anulado
        }

        public enum Tipo {
            ENTREGA,        // Remito de entrega normal
            DEVOLUCION      // Remito de devolución
        }

        // ==========================================
        // MÉTODOS
        // ==========================================

        public void agregarItem(RemitoItem item) {
            items.add(item);
            item.setRemito(this);
        }

        public void calcularTotal() {
            total = items.stream()
                    .map(RemitoItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

    @PrePersist
    protected void onCreate() {
        if (fechaEmision == null) {
            fechaEmision = LocalDate.now();
        }
    }
    }

