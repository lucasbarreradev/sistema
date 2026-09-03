package com.sistema.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistema.dto.AtributoVarianteMl;
import com.sistema.model.CanalVenta;
import com.sistema.model.Producto;
import com.sistema.model.ProductoVariante;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.beans.Introspector;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RevisionPublicacionServiceTest {
    private final ProductoRepository productos = mock(ProductoRepository.class);
    private final ProductoVarianteRepository variantes = mock(ProductoVarianteRepository.class);
    private final MercadoLibreAtributosVarianteService atributos =
            mock(MercadoLibreAtributosVarianteService.class);
    private final MercadoLibreOpcionesEnvioService opcionesEnvio =
            mock(MercadoLibreOpcionesEnvioService.class);
    private final RevisionPublicacionService service = new RevisionPublicacionService(
            productos, variantes, atributos, opcionesEnvio, new ObjectMapper());

    @Test
    void elDtoExponePropiedadesCompatiblesConJsp() throws Exception {
        Set<String> propiedades = java.util.Arrays.stream(
                        Introspector.getBeanInfo(
                                com.sistema.dto.RevisionProductoPublicacionDto.class)
                                .getPropertyDescriptors())
                .map(descriptor -> descriptor.getName())
                .collect(Collectors.toSet());

        assertTrue(propiedades.containsAll(Set.of(
                "producto", "variantes", "faltantes", "atributosFaltantes",
                "atributosObligatorios", "listo", "marcaObligatoria",
                "modeloObligatorio", "atributosGenerales", "atributosDeVariante",
                "valoresAtributosGenerales", "valoresAtributosVariantes",
                "faltaCategoriaMercadoLibre", "categoriaMercadoLibreNombre")));
    }

    @Test
    void marcaCategoriaComoPendienteSinPublicar() {
        Producto producto = producto(1L);
        when(productos.findAllById(List.of(1L))).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(List.of(1L)))
                .thenReturn(List.of());

        var revision = service.revisar(
                List.of(1L), List.of(CanalVenta.MERCADO_LIBRE)).get(0);

        assertFalse(revision.isListo());
        assertTrue(revision.faltantes().contains("Categoría de Mercado Libre"));
        verify(atributos).predecirCategoria("Producto de prueba");
        verify(atributos, never()).obtenerPorCategoria(any());
    }

    @Test
    void detectaLaCategoriaUsandoPrimeroElTituloDeCadaProducto() {
        Producto primero = producto(20L);
        Producto segundo = producto(21L);
        primero.setDescripcion("AROMATIZADOR ULTRASÓNICO THIMOTY");
        segundo.setDescripcion("Guante exfoliante");
        primero.setCategoriaOrigen("Hogar");
        segundo.setCategoriaOrigen("Hogar");
        primero.setFotoUrlExterna("https://img.test/20.jpg");
        segundo.setFotoUrlExterna("https://img.test/21.jpg");
        List<Long> ids = List.of(20L, 21L);
        when(productos.findAllById(ids)).thenReturn(List.of(primero, segundo));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(atributos.predecirCategoria("AROMATIZADOR ULTRASÓNICO THIMOTY"))
                .thenReturn("MLA123");
        when(atributos.predecirCategoria("Guante exfoliante"))
                .thenReturn("MLA456");
        when(atributos.obtenerPorCategoria("MLA123")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA123", List.of()));
        when(atributos.obtenerPorCategoria("MLA456")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA456", List.of()));

        var revisiones = service.revisar(
                ids, List.of(CanalVenta.MERCADO_LIBRE));

        assertEquals(2, revisiones.size());
        assertEquals("MLA123", primero.getMercadoLibreCategoriaId());
        assertEquals("MLA456", segundo.getMercadoLibreCategoriaId());
        verify(atributos).predecirCategoria("AROMATIZADOR ULTRASÓNICO THIMOTY");
        verify(atributos).predecirCategoria("Guante exfoliante");
        verify(atributos).obtenerPorCategoria("MLA123");
        verify(atributos).obtenerPorCategoria("MLA456");
        verify(productos).save(primero);
        verify(productos).save(segundo);
    }

    @Test
    void combinaTituloYCategoriaDeOrigenCuandoElTituloSoloNoAlcanza() {
        Producto producto = producto(22L);
        producto.setDescripcion("Difusor de Ambiente");
        producto.setCategoriaOrigen("Aromatizantes");
        producto.setFotoUrlExterna("https://img.test/22.jpg");
        List<Long> ids = List.of(22L);
        when(productos.findAllById(ids)).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(atributos.predecirCategoria("Difusor de Ambiente"))
                .thenReturn("");
        when(atributos.predecirCategoria(
                "Difusor de Ambiente Aromatizantes")).thenReturn("MLA789");
        when(atributos.obtenerPorCategoria("MLA789")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA789", List.of()));

        var revision = service.revisar(
                ids, List.of(CanalVenta.MERCADO_LIBRE)).get(0);

        assertEquals("MLA789", producto.getMercadoLibreCategoriaId());
        assertTrue(revision.faltantes().isEmpty());
        verify(atributos).predecirCategoria("Difusor de Ambiente");
        verify(atributos).predecirCategoria(
                "Difusor de Ambiente Aromatizantes");
    }

    @Test
    void informaElAtributoObligatorioQueFalta() {
        Producto producto = producto(2L);
        producto.setMercadoLibreCategoriaId("MLA123");
        producto.setFotoUrlExterna("https://img.test/foto.jpg");
        when(productos.findAllById(List.of(2L))).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(List.of(2L)))
                .thenReturn(List.of());
        when(atributos.obtenerPorCategoria("MLA123")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado("MLA123", List.of(
                        new AtributoVarianteMl("BRAND", "Marca", "string",
                                List.of(), List.of(), "", true, false))));

        var revision = service.revisar(
                List.of(2L), List.of(CanalVenta.MERCADO_LIBRE)).get(0);

        assertEquals(List.of("Marca"), revision.atributosFaltantes());
        assertEquals(List.of("Marca"), revision.atributosObligatorios());
        assertTrue(revision.isMarcaObligatoria());
        assertFalse(revision.isModeloObligatorio());
        assertEquals(List.of("BRAND"), revision.atributosGenerales().stream()
                .map(AtributoVarianteMl::id).toList());
        assertTrue(revision.atributosDeVariante().isEmpty());
        assertTrue(revision.faltantes().get(0).contains("Marca"));
    }

    @Test
    void consultaUnaCategoriaUnaSolaVezParaTodoElLote() {
        Producto primero = producto(10L);
        Producto segundo = producto(11L);
        primero.setMercadoLibreCategoriaId("MLA123");
        segundo.setMercadoLibreCategoriaId("MLA123");
        primero.setFotoUrlExterna("https://img.test/1.jpg");
        segundo.setFotoUrlExterna("https://img.test/2.jpg");
        List<Long> ids = List.of(10L, 11L);
        when(productos.findAllById(ids)).thenReturn(List.of(primero, segundo));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(atributos.obtenerPorCategoria("MLA123")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA123", List.of()));

        assertEquals(2, service.revisar(
                ids, List.of(CanalVenta.MERCADO_LIBRE)).size());

        verify(atributos).obtenerPorCategoria("MLA123");
    }

    @Test
    void completaLasOpcionesDeEnvioQueMercadoLibrePermite() {
        Producto producto = producto(31L);
        producto.setMercadoLibreCategoriaId("MLA412620");
        producto.setMercadoLibreModoEnvio("not_specified");
        producto.setMercadoLibreEnvioGratis(false);
        producto.setMercadoLibreRetiroPersonal(false);
        producto.setFotoUrlExterna("https://img.test/31.jpg");
        List<Long> ids = List.of(31L);
        when(productos.findAllById(ids)).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(opcionesEnvio.obtener(producto)).thenReturn(
                new MercadoLibreOpcionesEnvioService.OpcionesEnvio(
                        "me2", true, true, true));
        when(atributos.obtenerPorCategoria("MLA412620")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA412620", "Dispensers de Jabón", List.of()));

        service.revisar(ids, List.of(CanalVenta.MERCADO_LIBRE));

        assertEquals("me2", producto.getMercadoLibreModoEnvio());
        assertEquals(true, producto.getMercadoLibreEnvioGratis());
        assertEquals(true, producto.getMercadoLibreRetiroPersonal());
        verify(productos).save(producto);
    }

    @Test
    void conservaElResultadoPreparadoParaAbrirLaTablaSinReconsultar() {
        Producto producto = producto(13L);
        producto.setMercadoLibreCategoriaId("MLA123");
        producto.setFotoUrlExterna("https://img.test/13.jpg");
        List<Long> ids = List.of(13L);
        when(productos.findAllById(ids)).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(atributos.obtenerPorCategoria("MLA123")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA123", List.of()));

        int procesados = service.prepararEnSegundoPlano(
                90L, ids, List.of(CanalVenta.MERCADO_LIBRE), () -> false);

        assertEquals(1, procesados);
        assertEquals(1, service.consumirRevisionPreparada(90L)
                .orElseThrow().size());
        assertTrue(service.consumirRevisionPreparada(90L).isEmpty());
        verify(atributos).obtenerPorCategoria("MLA123");
    }

    @Test
    void reemplazaUnaCategoriaManualQueMercadoLibreYaNoReconoce() {
        Producto producto = producto(14L);
        producto.setMercadoLibreCategoriaId("MLA438180");
        producto.setMercadoLibreCategoriaFijada(true);
        producto.setFotoUrlExterna("https://img.test/14.jpg");
        List<Long> ids = List.of(14L);
        when(productos.findAllById(ids)).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(atributos.obtenerPorCategoria("MLA438180")).thenThrow(
                HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY,
                        "{\"message\":\"Category not found: MLA438180\"}"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.charset.StandardCharsets.UTF_8));
        when(atributos.predecirCategoria("Producto de prueba"))
                .thenReturn("MLA999");
        when(atributos.obtenerPorCategoria("MLA999")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA999", List.of()));

        var revision = service.revisar(
                ids, List.of(CanalVenta.MERCADO_LIBRE)).get(0);

        assertEquals("MLA999", producto.getMercadoLibreCategoriaId());
        assertEquals(false, producto.getMercadoLibreCategoriaFijada());
        assertTrue(revision.faltantes().isEmpty());
        verify(productos).save(producto);
        verify(atributos).predecirCategoria("Producto de prueba");
        verify(atributos).obtenerPorCategoria("MLA999");
    }

    @Test
    void muestraLosAtributosDeUnaUnicaVarianteEnLaRevision() {
        Producto producto = producto(12L);
        producto.setMercadoLibreCategoriaId("MLA123");
        producto.setFotoUrlExterna("https://img.test/12.jpg");
        ProductoVariante variante = new ProductoVariante();
        variante.setId(120L);
        variante.setProducto(producto);
        variante.setNombre("Black vetiver");
        variante.setColor("Negro");
        variante.setStock(2);
        variante.setPrecioContado(BigDecimal.TEN);
        List<Long> ids = List.of(12L);
        when(productos.findAllById(ids)).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of(variante));
        when(atributos.obtenerPorCategoria("MLA123")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA123", List.of(new AtributoVarianteMl(
                        "COLOR", "Color", "string", List.of("Negro", "Blanco"),
                        List.of(), "", true, true))));

        var revision = service.revisar(
                ids, List.of(CanalVenta.MERCADO_LIBRE)).get(0);

        assertEquals(List.of("COLOR"), revision.atributosDeVariante().stream()
                .map(AtributoVarianteMl::id).toList());
        assertEquals("Negro",
                revision.valoresAtributosVariantes().get(120L).get("COLOR"));
    }

    @Test
    void guardaDatosGeneralesStockYPrecioDeCadaVariante() {
        Producto producto = producto(3L);
        ProductoVariante variante = new ProductoVariante();
        variante.setId(30L);
        variante.setProducto(producto);
        variante.setStock(1);
        variante.setPrecioContado(BigDecimal.TEN);
        when(productos.findById(3L)).thenReturn(Optional.of(producto));
        when(variantes.findByProductoIdOrderByNombreAsc(3L)).thenReturn(List.of(variante));

        service.actualizar(
                3L, "Título nuevo", "Descripción nueva", "MLA999",
                "BIG MISTY", "BM-500", null, new BigDecimal("8000"),
                "me2", true, true, "gold_pro",
                List.of(30L), List.of(7), List.of(new BigDecimal("9500")),
                java.util.Map.of(
                        "ml_general_BRAND", "Marca editada",
                        "ml_variante_30_COLOR", "Negro"));

        assertEquals("Título nuevo", producto.getMercadoLibreTitulo());
        assertEquals("Producto de prueba", producto.getDescripcion());
        assertEquals("MLA999", producto.getMercadoLibreCategoriaId());
        assertEquals("Marca editada", producto.getMercadoLibreMarca());
        assertEquals("gold_pro", producto.getMercadoLibreListingTypeId());
        assertEquals(7, producto.getCantidad());
        assertEquals(7, variante.getStock());
        assertEquals(new BigDecimal("9500"), variante.getPrecioContado());
        assertEquals(null, variante.getColor());
        assertEquals("{\"COLOR\":\"Negro\"}",
                variante.getMercadoLibreAtributosJson());
        assertTrue(producto.getMercadoLibreEnvioGratis());
        assertTrue(producto.getMercadoLibreRetiroPersonal());
        verify(variantes).saveAll(any());
        verify(productos).save(producto);
    }

    @Test
    void detectaLaCategoriaAlGuardarUnTituloNuevoConCategoriaVacia() {
        Producto producto = producto(23L);
        producto.setFotoUrlExterna("https://img.test/23.jpg");
        when(productos.findById(23L)).thenReturn(Optional.of(producto));
        when(variantes.findByProductoIdOrderByNombreAsc(23L))
                .thenReturn(List.of());

        service.actualizar(
                23L, "Aromatizador ultrasónico Thimoty", "", "",
                "", "", 2, new BigDecimal("1000"), "",
                false, false, "", null, null, List.of(), Map.of());

        List<Long> ids = List.of(23L);
        when(productos.findAllById(ids)).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(atributos.predecirCategoria("Aromatizador ultrasónico Thimoty"))
                .thenReturn("MLA381270");
        when(atributos.obtenerPorCategoria("MLA381270")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA381270", List.of()));

        var revision = service.revisar(
                ids, List.of(CanalVenta.MERCADO_LIBRE)).get(0);

        assertEquals("Aromatizador ultrasónico Thimoty",
                producto.getMercadoLibreTitulo());
        assertEquals("Producto de prueba", producto.getDescripcion());
        assertEquals("MLA381270", producto.getMercadoLibreCategoriaId());
        assertTrue(revision.isListo());
        verify(atributos).predecirCategoria(
                "Aromatizador ultrasónico Thimoty");
    }

    @Test
    void muestraElNombreDeLaCategoriaYConservaElIdInterno() {
        Producto producto = producto(24L);
        producto.setMercadoLibreCategoriaId("MLA387586");
        producto.setFotoUrlExterna("https://img.test/24.jpg");
        List<Long> ids = List.of(24L);
        when(productos.findAllById(ids)).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(atributos.obtenerPorCategoria("MLA387586")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA387586", "Sahumerios", List.of()));

        var revision = service.revisar(
                ids, List.of(CanalVenta.MERCADO_LIBRE)).get(0);

        assertEquals("Sahumerios", revision.categoriaMercadoLibreNombre());
        assertEquals("MLA387586", producto.getMercadoLibreCategoriaId());
    }

    @Test
    void convierteElNombreDeCategoriaEnIdAlGuardar() {
        Producto producto = producto(25L);
        when(productos.findById(25L)).thenReturn(Optional.of(producto));
        when(variantes.findByProductoIdOrderByNombreAsc(25L))
                .thenReturn(List.of());
        when(atributos.predecirCategoria("Sahumerios"))
                .thenReturn("MLA387586");

        service.actualizar(
                25L, "Incienso", "", "Sahumerios",
                "", "", 2, new BigDecimal("1000"), "",
                false, false, "", null, null, List.of(), Map.of());

        assertEquals("MLA387586", producto.getMercadoLibreCategoriaId());
        assertEquals(true, producto.getMercadoLibreCategoriaFijada());
        verify(atributos).predecirCategoria("Sahumerios");
    }

    @Test
    void cambiaSolamenteLaCategoriaParaRecargarSusAtributos() {
        Producto producto = producto(28L);
        producto.setDescripcion("Título que debe conservarse");
        producto.setCantidad(8);
        when(productos.findById(28L)).thenReturn(Optional.of(producto));
        when(atributos.predecirCategoria("Riñoneras"))
                .thenReturn("MLA417710");

        service.actualizarCategoria(28L, "Riñoneras");

        assertEquals("MLA417710", producto.getMercadoLibreCategoriaId());
        assertEquals(true, producto.getMercadoLibreCategoriaFijada());
        assertEquals("Título que debe conservarse", producto.getDescripcion());
        assertEquals(8, producto.getCantidad());
        verify(productos).save(producto);
        verify(atributos).predecirCategoria("Riñoneras");
    }

    @Test
    void rechazaUnNombreDeCategoriaQueMercadoLibreNoPuedeDetectar() {
        Producto producto = producto(26L);
        when(productos.findById(26L)).thenReturn(Optional.of(producto));
        when(atributos.predecirCategoria("Categoría inexistente"))
                .thenReturn("");

        var error = assertThrows(IllegalArgumentException.class, () ->
                service.actualizar(
                        26L, "Producto", "", "Categoría inexistente",
                        "", "", 2, new BigDecimal("1000"), "",
                        false, false, "", null, null, List.of(), Map.of()));

        assertTrue(error.getMessage().contains("nombre más específico"));
    }

    @Test
    void vuelveADetectarUnaCategoriaGuardadaDuranteLaPreparacion() {
        Producto producto = producto(27L);
        producto.setDescripcion("Billetera Capibara");
        producto.setMercadoLibreCategoriaId("MLA380650");
        producto.setFotoUrlExterna("https://img.test/27.jpg");
        List<Long> ids = List.of(27L);
        when(productos.findAllById(ids)).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(atributos.predecirCategoria("Billetera Capibara"))
                .thenReturn("MLA417712");
        when(atributos.obtenerPorCategoria("MLA417712")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA417712", "Billeteras", List.of()));

        int procesados = service.prepararEnSegundoPlano(
                91L, ids, List.of(CanalVenta.MERCADO_LIBRE), () -> false);

        assertEquals(1, procesados);
        assertEquals("MLA417712", producto.getMercadoLibreCategoriaId());
        assertEquals("Billeteras", service.consumirRevisionPreparada(91L)
                .orElseThrow().get(0).categoriaMercadoLibreNombre());
        verify(productos).save(producto);
    }

    @Test
    void conservaUnaCategoriaFijadaOAnteriorAlVolverAPreparar() {
        Producto producto = producto(29L);
        producto.setDescripcion("Botellón Suavizante/Jabón Liquido");
        producto.setMercadoLibreCategoriaId("MLA412620");
        producto.setMercadoLibreCategoriaFijada(null);
        producto.setFotoUrlExterna("https://img.test/29.jpg");
        List<Long> ids = List.of(29L);
        when(productos.findAllById(ids)).thenReturn(List.of(producto));
        when(variantes.findByProductoIdInOrderByProductoIdAscNombreAsc(ids))
                .thenReturn(List.of());
        when(atributos.obtenerPorCategoria("MLA412620")).thenReturn(
                new MercadoLibreAtributosVarianteService.Resultado(
                        "MLA412620", "Dispensers de Jabón", List.of()));

        int procesados = service.prepararEnSegundoPlano(
                92L, ids, List.of(CanalVenta.MERCADO_LIBRE), () -> false);

        assertEquals(1, procesados);
        assertEquals("MLA412620", producto.getMercadoLibreCategoriaId());
        assertEquals("Dispensers de Jabón", service.consumirRevisionPreparada(92L)
                .orElseThrow().get(0).categoriaMercadoLibreNombre());
        verify(atributos, never()).predecirCategoria(anyString());
        verify(productos, never()).save(producto);
    }

    private Producto producto(Long id) {
        Producto producto = new Producto();
        producto.setId(id);
        producto.setSku("SKU-" + id);
        producto.setDescripcion("Producto de prueba");
        producto.setCantidad(2);
        producto.setPrecioContado(new BigDecimal("1000"));
        return producto;
    }
}
