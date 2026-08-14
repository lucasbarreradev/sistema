package com.sistema.service;

import com.sistema.model.AfipFacturaRequest;
import com.sistema.model.AfipFacturaResponse;
import com.sistema.model.TipoComprobante;
import com.sistema.tenant.TenantContext;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AfipClient {
    private static final String WSAA_URL = "https://wsaahomo.afip.gov.ar/ws/services/LoginCms";
    private static final String WSFE_URL = "https://wswhomo.afip.gov.ar/wsfev1/service.asmx";
    private static final String NS_WSFE = "http://ar.gov.afip.dif.FEV1/";
    private static final DateTimeFormatter FECHA = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestClient restClient = RestClient.builder().build();
    private final ConfiguracionArcaService configuracionService;
    private final Map<Long, TicketAcceso> tickets = new ConcurrentHashMap<>();

    public AfipClient(ConfiguracionArcaService configuracionService) {
        this.configuracionService = configuracionService;
    }

    public void probarConexion() {
        ConfiguracionArcaService.Credenciales c = configuracionService.obtenerCredenciales();
        TicketAcceso ticket = ticket();
        String cuerpo = "<ar:FECompUltimoAutorizado>" + auth(ticket, c.cuit())
                + "<ar:PtoVta>" + c.puntoVenta() + "</ar:PtoVta><ar:CbteTipo>11</ar:CbteTipo>"
                + "</ar:FECompUltimoAutorizado>";
        Document respuesta = invocarWsfe("FECompUltimoAutorizado", cuerpo);
        errores(respuesta);
    }

    public Long obtenerUltimoNumero(Integer puntoVenta, TipoComprobante tipo) {
        ConfiguracionArcaService.Credenciales c = configuracionService.obtenerCredenciales();
        TicketAcceso ticket = ticket();
        String cuerpo = "<ar:FECompUltimoAutorizado>" + auth(ticket, c.cuit())
                + "<ar:PtoVta>" + puntoVenta + "</ar:PtoVta>"
                + "<ar:CbteTipo>" + codigo(tipo) + "</ar:CbteTipo>"
                + "</ar:FECompUltimoAutorizado>";
        Document respuesta = invocarWsfe("FECompUltimoAutorizado", cuerpo);
        errores(respuesta);
        return Long.parseLong(texto(respuesta, "CbteNro", "0"));
    }

    public AfipFacturaResponse facturar(AfipFacturaRequest request) {
        ConfiguracionArcaService.Credenciales c = configuracionService.obtenerCredenciales();
        TicketAcceso ticket = ticket();
        String detalle = construirDetalle(request);

        String cuerpo = "<ar:FECAESolicitar>" + auth(ticket, c.cuit())
                + "<ar:FeCAEReq><ar:FeCabReq><ar:CantReg>1</ar:CantReg>"
                + "<ar:PtoVta>" + request.getPuntoVenta() + "</ar:PtoVta>"
                + "<ar:CbteTipo>" + codigo(request.getTipoComprobante()) + "</ar:CbteTipo>"
                + "</ar:FeCabReq><ar:FeDetReq><ar:FECAEDetRequest>" + detalle
                + "</ar:FECAEDetRequest></ar:FeDetReq></ar:FeCAEReq></ar:FECAESolicitar>";
        Document respuesta = invocarWsfe("FECAESolicitar", cuerpo);
        errores(respuesta);
        String resultado = texto(respuesta, "Resultado", "R");
        String cae = texto(respuesta, "CAE", null);
        if (!"A".equals(resultado) || cae == null || cae.isBlank()) {
            String observaciones = mensajes(respuesta, "Obs", "Code", "Msg");
            throw new IllegalStateException("ARCA rechazó el comprobante"
                    + (observaciones.isBlank() ? "" : ": " + observaciones));
        }
        LocalDate vencimiento = LocalDate.parse(texto(respuesta, "CAEFchVto", ""), FECHA);
        return new AfipFacturaResponse(cae, vencimiento, request.getNumeroComprobante());
    }

    String construirDetalle(AfipFacturaRequest request) {
        StringBuilder detalle = new StringBuilder()
                .append("<ar:Concepto>1</ar:Concepto>")
                .append("<ar:DocTipo>").append(request.getTipoDocumento()).append("</ar:DocTipo>")
                .append("<ar:DocNro>").append(request.getNumeroDocumento()).append("</ar:DocNro>")
                .append("<ar:CbteDesde>").append(request.getNumeroComprobante()).append("</ar:CbteDesde>")
                .append("<ar:CbteHasta>").append(request.getNumeroComprobante()).append("</ar:CbteHasta>")
                .append("<ar:CbteFch>").append(FECHA.format(request.getFechaComprobante())).append("</ar:CbteFch>")
                .append("<ar:ImpTotal>").append(monto(request.getImporteTotal())).append("</ar:ImpTotal>")
                .append("<ar:ImpTotConc>0.00</ar:ImpTotConc>")
                .append("<ar:ImpNeto>").append(monto(request.getImporteNeto())).append("</ar:ImpNeto>")
                .append("<ar:ImpOpEx>").append(monto(request.getImporteExento())).append("</ar:ImpOpEx>")
                .append("<ar:ImpTrib>0.00</ar:ImpTrib>")
                .append("<ar:ImpIVA>").append(monto(request.getImporteIva())).append("</ar:ImpIVA>")
                .append("<ar:MonId>PES</ar:MonId><ar:MonCotiz>1.000000</ar:MonCotiz>")
                .append("<ar:CondicionIVAReceptorId>")
                .append(request.getCondicionIvaReceptorId())
                .append("</ar:CondicionIVAReceptorId>");
        AfipFacturaRequest.ComprobanteAsociado asociado = request.getComprobanteAsociado();
        if (asociado != null) {
            detalle.append("<ar:CbtesAsoc><ar:CbteAsoc>")
                    .append("<ar:Tipo>").append(codigo(asociado.tipo())).append("</ar:Tipo>")
                    .append("<ar:PtoVta>").append(asociado.puntoVenta()).append("</ar:PtoVta>")
                    .append("<ar:Nro>").append(asociado.numero()).append("</ar:Nro>");
            if (asociado.fecha() != null) {
                detalle.append("<ar:CbteFch>").append(FECHA.format(asociado.fecha()))
                        .append("</ar:CbteFch>");
            }
            detalle.append("</ar:CbteAsoc></ar:CbtesAsoc>");
        }
        if (request.getAlicuotas() != null && !request.getAlicuotas().isEmpty()) {
            detalle.append("<ar:Iva>");
            for (AfipFacturaRequest.Alicuota a : request.getAlicuotas()) {
                detalle.append("<ar:AlicIva><ar:Id>").append(a.codigo()).append("</ar:Id>")
                        .append("<ar:BaseImp>").append(monto(a.baseImponible())).append("</ar:BaseImp>")
                        .append("<ar:Importe>").append(monto(a.importe())).append("</ar:Importe></ar:AlicIva>");
            }
            detalle.append("</ar:Iva>");
        }
        return detalle.toString();
    }

    private TicketAcceso ticket() {
        long tenantId = TenantContext.require();
        TicketAcceso existente = tickets.get(tenantId);
        if (existente != null && existente.expira().isAfter(Instant.now().plusSeconds(300))) return existente;
        TicketAcceso nuevo = solicitarTicket();
        tickets.put(tenantId, nuevo);
        return nuevo;
    }

    private TicketAcceso solicitarTicket() {
        ConfiguracionArcaService.Credenciales c = configuracionService.obtenerCredenciales();
        X509Certificate certificado = configuracionService.leerCertificado(c.certificadoPem());
        PrivateKey clave = configuracionService.leerClavePrivada(c.clavePrivadaPem());
        Instant ahora = Instant.now();
        String tra = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<loginTicketRequest version=\"1.0\"><header><uniqueId>" + ahora.getEpochSecond()
                + "</uniqueId><generationTime>" + OffsetDateTime.ofInstant(ahora.minusSeconds(300), ZoneOffset.UTC)
                + "</generationTime><expirationTime>" + OffsetDateTime.ofInstant(ahora.plusSeconds(36000), ZoneOffset.UTC)
                + "</expirationTime></header><service>wsfe</service></loginTicketRequest>";
        String cms = firmarCms(tra, certificado, clave);
        String soap = sobre("<loginCms xmlns=\"http://wsaa.view.sua.dvadac.desein.afip.gov\">"
                + "<in0>" + cms + "</in0></loginCms>", null);
        String respuesta = restClient.post().uri(WSAA_URL)
                .contentType(MediaType.TEXT_XML)
                .header("SOAPAction", "urn:LoginCms")
                .body(soap).retrieve().body(String.class);
        Document soapDoc = xml(respuesta);
        fault(soapDoc);
        String ticketXml = texto(soapDoc, "loginCmsReturn", null);
        if (ticketXml == null) throw new IllegalStateException("ARCA no devolvió el ticket de acceso");
        Document ticket = xml(ticketXml);
        String token = texto(ticket, "token", null);
        String sign = texto(ticket, "sign", null);
        String expiracion = texto(ticket, "expirationTime", null);
        if (token == null || sign == null || expiracion == null) throw new IllegalStateException("El ticket de ARCA está incompleto");
        return new TicketAcceso(token, sign, OffsetDateTime.parse(expiracion).toInstant());
    }

    private String firmarCms(String tra, X509Certificate certificado, PrivateKey clave) {
        try {
            String algoritmo = clave.getAlgorithm().equalsIgnoreCase("EC") ? "SHA256withECDSA" : "SHA256withRSA";
            CMSSignedDataGenerator generador = new CMSSignedDataGenerator();
            generador.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().build())
                    .build(new JcaContentSignerBuilder(algoritmo).build(clave), certificado));
            generador.addCertificates(new JcaCertStore(List.of(certificado)));
            CMSSignedData firmado = generador.generate(
                    new CMSProcessableByteArray(tra.getBytes(StandardCharsets.UTF_8)), true);
            return Base64.getEncoder().encodeToString(firmado.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar la solicitud a ARCA", e);
        }
    }

    private Document invocarWsfe(String metodo, String cuerpo) {
        String respuesta = restClient.post().uri(WSFE_URL)
                .contentType(MediaType.TEXT_XML)
                .header("SOAPAction", "\"" + NS_WSFE + metodo + "\"")
                .body(sobre(cuerpo, "xmlns:ar=\"" + NS_WSFE + "\""))
                .retrieve().body(String.class);
        Document documento = xml(respuesta);
        fault(documento);
        return documento;
    }

    private String auth(TicketAcceso ticket, String cuit) {
        return "<ar:Auth><ar:Token>" + escapar(ticket.token()) + "</ar:Token><ar:Sign>"
                + escapar(ticket.sign()) + "</ar:Sign><ar:Cuit>" + cuit + "</ar:Cuit></ar:Auth>";
    }

    private String sobre(String cuerpo, String atributos) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + (atributos == null ? "" : atributos) + "><soap:Body>" + cuerpo
                + "</soap:Body></soap:Envelope>";
    }

    private Document xml(String contenido) {
        if (contenido == null || contenido.isBlank()) throw new IllegalStateException("ARCA devolvió una respuesta vacía");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(contenido)));
        } catch (Exception e) {
            throw new IllegalStateException("ARCA devolvió XML inválido", e);
        }
    }

    private void fault(Document doc) {
        String mensaje = texto(doc, "faultstring", null);
        if (mensaje != null) throw new IllegalStateException("ARCA: " + mensaje);
    }

    private void errores(Document doc) {
        String errores = mensajes(doc, "Err", "Code", "Msg");
        if (!errores.isBlank()) throw new IllegalStateException("ARCA: " + errores);
    }

    private String mensajes(Document doc, String item, String codigo, String mensaje) {
        try {
            var nodos = (org.w3c.dom.NodeList) XPathFactory.newInstance().newXPath().evaluate(
                    "//*[local-name()='" + item + "']", doc, XPathConstants.NODESET);
            List<String> valores = new ArrayList<>();
            for (int i = 0; i < nodos.getLength(); i++) {
                Node nodo = nodos.item(i);
                String c = texto(nodo, codigo);
                String m = texto(nodo, mensaje);
                if (m != null) valores.add((c == null ? "" : c + " - ") + m);
            }
            return String.join("; ", valores);
        } catch (Exception e) { return ""; }
    }

    private String texto(Document doc, String nombre, String defecto) {
        try {
            String valor = (String) XPathFactory.newInstance().newXPath().evaluate(
                    "string(//*[local-name()='" + nombre + "'][1])", doc, XPathConstants.STRING);
            return valor == null || valor.isBlank() ? defecto : valor.trim();
        } catch (Exception e) { return defecto; }
    }

    private String texto(Node nodo, String nombre) {
        try {
            String valor = (String) XPathFactory.newInstance().newXPath().evaluate(
                    "string(.//*[local-name()='" + nombre + "'][1])", nodo, XPathConstants.STRING);
            return valor == null || valor.isBlank() ? null : valor.trim();
        } catch (Exception e) { return null; }
    }

    private int codigo(TipoComprobante tipo) {
        return tipo.getCodigoArca();
    }

    private String monto(java.math.BigDecimal valor) {
        return (valor == null ? java.math.BigDecimal.ZERO : valor)
                .setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
    private String escapar(String valor) {
        return valor.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private record TicketAcceso(String token, String sign, Instant expira) {}
}
