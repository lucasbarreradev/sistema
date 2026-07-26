package com.sistema.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(uniqueConstraints =
        @UniqueConstraint(name = "uk_venta_tenant_codigo", columnNames = {"tenant_id", "codigo"}))
@ToString(exclude = {"items", "cliente"})
@Getter
@Setter
public class Venta extends TenantAwareEntity {
    // ==========================================
    // Estado de la venta
    // ==========================================
    public enum Estado {
        BORRADOR,
        CONFIRMADA,
        FACTURADA,
        ANULADA,
        COMPLETADA
    }

    // ==========================================
    // Origen de la venta
    // ==========================================
    public enum Origen {
        DIRECTA,
        PRESUPUESTO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @ManyToOne
    @JoinColumn(name="cliente_id", nullable = true)
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    private TipoComprobante tipoComprobante;

    private Integer puntoVenta;
    private Long numeroComprobante;

    private String cae;
    private LocalDate fechaVencimientoCae;

    // Origen de la venta
    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false)
    private Origen origen;

    // Estado actual
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private Estado estado;

    // Forma de pago
    @Enumerated(EnumType.STRING)
    private FormaPago formaPago;

    // Referencia al presupuesto si aplica
    @Column(name = "presupuesto_codigo", length = 20)
    private String presupuestoCodigo;

    // Nota
    @Column(name = "nota", length = 500)
    private String nota;

    @Column(name = "fecha_venta", nullable = false)
    private LocalDateTime fechaVenta;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    // ==========================================
    // Items de la venta
    // ==========================================
    @OneToMany(
            mappedBy = "venta",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<VentaItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalNeto = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalIva = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    // ==========================================
    // Constructores
    // ==========================================
    public Venta() {
    }

    public Venta(String codigo,
                 Cliente cliente,
                 Origen origen,
                 FormaPago formaPago,
                 String presupuestoCodigo,
                 String nota) {

        this.codigo = codigo;
        this.cliente = cliente;
        this.origen = origen;
        this.formaPago = formaPago;
        this.presupuestoCodigo = presupuestoCodigo;
        this.nota = nota;
        this.estado = Estado.COMPLETADA;
        this.fechaVenta = LocalDateTime.now();
    }


    // ==========================================
    // Lógica
    // ==========================================
    public void agregarItem(VentaItem item) {
        item.setVenta(this);
        items.add(item);
    }

    public void calcularTotal() {
        this.total = items.stream()
                .map(VentaItem::getSubtotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void calcularTotales() {

        BigDecimal netoAcum = BigDecimal.ZERO;
        BigDecimal ivaAcum = BigDecimal.ZERO;

        for (VentaItem item : items) {
            netoAcum = netoAcum.add(item.getNeto());
            ivaAcum = ivaAcum.add(item.getIva());
        }

        this.totalNeto = netoAcum;
        this.totalIva = ivaAcum;
        this.total = netoAcum.add(ivaAcum);
    }

    // ==========================================
    // Getters y Setters
    // ==========================================
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public Origen getOrigen() {
        return origen;
    }

    public void setOrigen(Origen origen) {
        this.origen = origen;
    }

    public Estado getEstado() {
        return estado;
    }

    public void setEstado(Estado estado) {
        this.estado = estado;
    }

    public FormaPago getFormaPago() {
        return formaPago;
    }

    public void setFormaPago(FormaPago formaPago) {
        this.formaPago = formaPago;
    }

    public String getPresupuestoCodigo() {
        return presupuestoCodigo;
    }

    public void setPresupuestoCodigo(String presupuestoCodigo) {
        this.presupuestoCodigo = presupuestoCodigo;
    }

    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }

    public LocalDateTime getFechaVenta() {
        return fechaVenta;
    }

    public LocalDateTime getFechaAnulacion() {
        return fechaAnulacion;
    }

    public void setFechaAnulacion(LocalDateTime fechaAnulacion) {
        this.fechaAnulacion = fechaAnulacion;
    }

    public List<VentaItem> getItems() {
        return items;
    }

    public void setItems(List<VentaItem> items) {
        this.items = items;
    }

    public BigDecimal getTotal() {
        return total != null ? total : BigDecimal.ZERO;
    }


    public String getFechaFormateada() {
        return fechaVenta != null
                ? fechaVenta.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "";
    }


}
