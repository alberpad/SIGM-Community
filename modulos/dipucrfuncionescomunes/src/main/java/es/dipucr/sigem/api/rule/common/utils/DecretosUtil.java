package es.dipucr.sigem.api.rule.common.utils;

import ieci.tdw.ispac.api.IEntitiesAPI;
import ieci.tdw.ispac.api.IInvesflowAPI;
import ieci.tdw.ispac.api.ITXTransaction;
import ieci.tdw.ispac.api.entities.SpacEntities;
import ieci.tdw.ispac.api.errors.ISPACException;
import ieci.tdw.ispac.api.errors.ISPACRuleException;
import ieci.tdw.ispac.api.item.IItem;
import ieci.tdw.ispac.api.item.IItemCollection;
import ieci.tdw.ispac.api.item.IProcedure;
import ieci.tdw.ispac.api.item.IProcess;
import ieci.tdw.ispac.api.rule.EventManager;
import ieci.tdw.ispac.api.rule.IRuleContext;
import ieci.tdw.ispac.ispaclib.context.ClientContext;
import ieci.tdw.ispac.ispaclib.context.IClientContext;
import ieci.tdw.ispac.ispaclib.gendoc.openoffice.OpenOfficeHelper;
import ieci.tdw.ispac.ispaclib.util.FileTemporaryManager;
import ieci.tdw.ispac.ispaclib.utils.StringUtils;

import java.awt.Color;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import org.apache.log4j.Logger;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import es.dipucr.sigem.api.rule.procedures.Constants;

/**
 * [ecenpri-Felipe] Clase con funciones comunes para decretos
 * 
 * @since 08.07.2011
 * @author Felipe
 */
public class DecretosUtil {

	/** Logger de la clase. */
	protected static final Logger LOGGER = Logger.getLogger(DecretosUtil.class);

	protected static final String _REGLA_INIT_DECRETO = "DipucrInitDecretoRelacionadoRule";
	public static final String DECRETO = "DECRETO";
	/**
	  *	Tabla con los datos del decreto
	  * Tabla SGD_DECRETO
	  **/
	public interface DecretoTabla{
		public static final String NOMBRE_TABLA = "SGD_DECRETO";
		
		
		
		public static final String NUMEXP = "NUMEXP";
		public static final String ANIO = "ANIO";
		public static final String NUMERO_DECRETO = "NUMERO_DECRETO";
		public static final String FECHA_DECRETO = "FECHA_DECRETO";
		public static final String EXTRACTO_DECRETO = "EXTRACTO_DECRETO";
	}
	
	public static IItem getDecreto(IClientContext cct, String numexp) throws ISPACRuleException{
		IItem decreto = null;
		
		try {
			// *********************************************
			IInvesflowAPI invesFlowAPI = cct.getAPI();
			IEntitiesAPI entitiesAPI = invesFlowAPI.getEntitiesAPI();
			// *********************************************
			
			IItemCollection colDecreto = entitiesAPI.getEntities(DecretoTabla.NOMBRE_TABLA, numexp);
			Iterator<?> iterDecreto = colDecreto.iterator();
			if (iterDecreto.hasNext()) {
				decreto = (IItem) iterDecreto.next();
			}

		} catch (Exception e) {
			LOGGER.error( "Error al obtener el decreto: " + numexp + ". " + e.getMessage(), e);
			throw new ISPACRuleException( "Error al obtener el decreto: " + numexp + ". " + e.getMessage(), e);
		}
		
		return decreto;
	}
	public static IItem getDocDecretoByNumExpDecreto(IClientContext cct, String numExpDecreto) throws ISPACRuleException{
		IItem acuerdo = null;
		try{
			Vector<String> colOrgaCol = ExpedientesRelacionadosUtil.getExpRelacionadosPadres(cct.getAPI().getEntitiesAPI(), numExpDecreto);
			for (int i=0; i<colOrgaCol.size(); i++){
				String consultaDecretos = "UPPER(NOMBRE) = 'DECRETO'";
				String orden = "ID DESC LIMIT 1";					

				IItemCollection icDoc = DocumentosUtil.getDocumentos(cct, numExpDecreto, consultaDecretos, orden);
				Iterator<?> docNotificaciones;
				
				if (icDoc.next()) {
					docNotificaciones = icDoc.iterator();
					while (docNotificaciones.hasNext()) {
						acuerdo =(IItem) docNotificaciones.next();
					}
				}
			}
		} catch (ISPACRuleException e) {
			LOGGER.error("Error al obtenerDecretoByNumExpDecreto en la decreto."+numExpDecreto+" - "+e.getMessage(),e);
			throw new ISPACRuleException("Error al obtenerDecretoByNumExpDecreto en la decreto."+numExpDecreto+" - "+e.getMessage(),e);
		} catch (ISPACException e) {
			LOGGER.error("Error al obtenerDecretoByNumExpDecreto en la decreto."+numExpDecreto+" - "+e.getMessage(),e);
			throw new ISPACRuleException("Error al obtenerDecretoByNumExpDecreto en la decreto."+numExpDecreto+" - "+e.getMessage(),e);
		}
		
		return acuerdo;
	}
	
	public static String getUltimoNumexpDecreto(IClientContext cct, String numexp) {
        return getNumexpDecreto(cct, numexp, QueryUtils.EXPRELACIONADOS.ORDER_DESC);
    }
    
    public static String getPrimerNumexpDecreto(IClientContext cct, String numexp) {
        return getNumexpDecreto(cct, numexp, QueryUtils.EXPRELACIONADOS.ORDER_ASC);
    }
        
    public static String getNumexpDecreto(IClientContext cct, String numexp, String orden) {
        String numexpDecreto = "";
        
        try{
            //Obtenemos el expediente de decreto
            IItemCollection expRelacionadosPadreCollection = cct.getAPI().getEntitiesAPI().queryEntities(Constants.TABLASBBDD.SPAC_EXP_RELACIONADOS, "WHERE " + ExpedientesRelacionadosUtil.NUMEXP_PADRE + " = '" + numexp + "' " + orden);
            Iterator<?> expRelacionadosPadreIterator = expRelacionadosPadreCollection.iterator();
            boolean encontrado = false;
            
            while (expRelacionadosPadreIterator.hasNext() && !encontrado){
                
                IItem expRel = (IItem)expRelacionadosPadreIterator.next();
                String numexpRel = "";
                if(StringUtils.isNotEmpty(expRel.getString(ExpedientesRelacionadosUtil.NUMEXP_HIJO)))numexpRel=expRel.getString(ExpedientesRelacionadosUtil.NUMEXP_HIJO);
                IItem expediente = ExpedientesUtil.getExpediente(cct, numexpRel);
                
                if(null != expediente){
                    String nombreProc = "";
                    if(StringUtils.isNotEmpty(expediente.getString(ExpedientesUtil.NOMBREPROCEDIMIENTO)))nombreProc=expediente.getString(ExpedientesUtil.NOMBREPROCEDIMIENTO);
                    
                    if(nombreProc.trim().toUpperCase().contains(DecretosUtil.DECRETO)){
                        numexpDecreto = numexpRel;
                        encontrado = true;
                    }
                }
            }
        } catch (ISPACException e ){
            LOGGER.error("ERROR al recuperar el expediente de decreto relacionado con el expediente: " + numexp + ". " + e.getMessage(), e);
        }
        return numexpDecreto;
    }

	/**
	 * Crea el decreto relacionado para el informe pasado como par�metro
	 * 
	 * @param rulectx
	 * @param nombreProc
	 * @param nombreDocInforme
	 * @param asunto
	 * @param extracto
	 * @param relacion
	 * @throws ISPACRuleException
	 * @return [Felipe #444] devolvemos el numexp del expediente generado
	 */
	public static String crearDecretoRelacionado(IRuleContext rulectx,
			String numexpActual, String nombreProc, String nombreDocInforme,
			String asunto, String extracto, String relacion)
			throws ISPACRuleException {
		String numexpNuevo = null; // [Felipe #444]

		try {
			// *********************************************
			IClientContext cct = rulectx.getClientContext();
			IInvesflowAPI invesFlowAPI = cct.getAPI();
			IEntitiesAPI entitiesAPI = invesFlowAPI.getEntitiesAPI();
			ITXTransaction tx = invesFlowAPI.getTransactionAPI();
			// *********************************************

			IItemCollection iCollection = null;

			// Recuperamos el procedimiento
			String strQuery = "WHERE UPPER(NOMBRE) LIKE UPPER('"
					+ nombreProc
					+ "') "
					+ "AND ID IN (SELECT ID FROM SPAC_P_PROCEDIMIENTOS WHERE ESTADO = "
					+ IProcedure.PCD_STATE_CURRENT + ")";
			iCollection = entitiesAPI.queryEntities("SPAC_CT_PROCEDIMIENTOS",
					strQuery);
			IItem itemProc = (IItem) iCollection.iterator().next();
			String codProc = itemProc.getString("COD_PCD");
			int idProc = itemProc.getInt("ID");

			// Creamos el expediente
			Map<String, String> params = new HashMap<String, String>();
			params.put("COD_PCD", codProc);
			int idExpediente = tx.createProcess(idProc, params);
			IProcess process = invesFlowAPI.getProcess(idExpediente);
			numexpNuevo = process.getString("NUMEXP");

			// Ponemos el asunto al expediente nuevo
			IItem itemExpedienteActual = ExpedientesUtil.getExpediente(cct,
					numexpActual);
			IItem itemExpedienteNuevo = ExpedientesUtil.getExpediente(cct,
					numexpNuevo);
			itemExpedienteNuevo.set("ASUNTO", asunto);
			itemExpedienteNuevo.set("IDENTIDADTITULAR",
					itemExpedienteActual.getString("IDENTIDADTITULAR"));
			itemExpedienteNuevo.set("NIFCIFTITULAR",
					itemExpedienteActual.getString("NIFCIFTITULAR"));
			itemExpedienteNuevo.store(cct);

			// Ponemos el extracto
			strQuery = "WHERE NUMEXP = '" + numexpNuevo + "'";
			iCollection = entitiesAPI.queryEntities("SGD_DECRETO", strQuery);
			IItem itemDecreto = (IItem) iCollection.iterator().next();
			itemDecreto.set("EXTRACTO_DECRETO", extracto);
			itemDecreto.set("AUTOMATIZACION", "SI"); // [dipucr-Felipe #1091]
			itemDecreto.store(cct);

			// Ponemos en los expedientes relacionados
			IItem itemRelacionados = entitiesAPI
					.createEntity(SpacEntities.SPAC_EXP_RELACIONADOS);
			itemRelacionados.set("NUMEXP_PADRE", numexpActual);
			itemRelacionados.set("NUMEXP_HIJO", numexpNuevo);
			itemRelacionados.set("RELACION", relacion);
			itemRelacionados.store(cct);

			// Ejecutar la regla asociada
			iCollection = invesFlowAPI.getCatalogAPI().getCTRules(
					_REGLA_INIT_DECRETO);
			IItem regla = (IItem) iCollection.iterator().next();
			int ruleId = regla.getInt("ID");
			rulectx.getClientContext().setSsVariable("nombreDocInforme",
					nombreDocInforme);
			EventManager eventmgr = new EventManager(cct);
			eventmgr.newContext();
			eventmgr.getRuleContextBuilder().addContext(process);
			eventmgr.processRule(ruleId);
		}

		catch (Exception e) {
			throw new ISPACRuleException(
					"Error al generar el nuevo expediente de " + nombreProc
							+ " a partir del informe de " + nombreDocInforme, e);
		}

		return numexpNuevo; // [Felipe #444]

	}

	@SuppressWarnings("unchecked")
	public static void crearDocInfNotifTra(OpenOfficeHelper ooHelper,
			IRuleContext rulectx, int codTramiteCreac)
			throws ISPACRuleException {
		try {
			// ------------------------------------------------------------------
			ClientContext cct = (ClientContext) rulectx.getClientContext();
			// ------------------------------------------------------------------
			
			String numExp = rulectx.getNumExp();
			
			if (ooHelper != null)
				ooHelper.dispose();

			// Creaci�n del documento 'Informaci�n de Notificaci�n y Traslado'
			String filePathDoc = FileTemporaryManager.getInstance()
					.getFileTemporaryPath()
					+ "/"
					+ FileTemporaryManager.getInstance().newFileName(".pdf");
			File ffilePathDoc = new File(filePathDoc);
			Document document = new Document(PageSize.A4);
			document.setMargins(document.leftMargin(), document.rightMargin(),
					document.topMargin(), 27);

			PdfWriter writer = PdfCopy.getInstance(document,
					new FileOutputStream(filePathDoc));

			writer.setPdfVersion(PdfCopy.VERSION_1_4);
			writer.setViewerPreferences(PdfCopy.PageModeUseOutlines);

			document.open();
			PdfUtil.nuevaPagina(document, true, true, true, PageSize.A4);

			Paragraph parrafo = new Paragraph();
			Font fuente = new Font(Font.TIMES_ROMAN);
			fuente.setStyle(Font.BOLD);
			fuente.setSize(15);
			StringBuilder texto = new StringBuilder("\n\n\n\n\n");

			PdfPTable tabla = new PdfPTable(2);

			// Obtengo los participantes interesados
			IItemCollection collectionPart = ParticipantesUtil
					.getParticipantes(cct, numExp,
							"(ROL = 'TRAS')", "NOMBRE");
			Iterator<IItem> itPart = collectionPart.iterator();
			texto.append("Traslados Exp. " + numExp + ": " + collectionPart.toList().size());
			Phrase frase = new Phrase(texto.toString(), fuente);
			parrafo.add(frase);

			PdfPCell cell = new PdfPCell();
			cell.setHorizontalAlignment(com.lowagie.text.Element.ALIGN_CENTER);
			cell.setBackgroundColor(Color.GRAY);
			Font font = new Font();
			font.setStyle(Font.BOLD);
			font.setSize(12);
			font.setFamily("Arial");
			cell.setPhrase(new Phrase("NOMBRE", font));
			tabla.addCell(cell);
			cell.setPhrase(new Phrase("EMAIL", font));
			tabla.addCell(cell);
			cell.setBackgroundColor(Color.WHITE);
			int contFilas = 0;
			while (itPart.hasNext()) {
				IItem participante = itPart.next();
				cell.setPhrase(new Phrase(participante.getString("NOMBRE")));
				tabla.addCell(cell);
				cell.setPhrase(new Phrase(participante
						.getString("DIRECCIONTELEMATICA")));
				tabla.addCell(cell);
				contFilas++;
				if (contFilas == 34) {
					contFilas = 0;
					parrafo.add(tabla);
					document.add(parrafo);
					PdfUtil.nuevaPagina(document, true, true, true,
							PageSize.A4);
					tabla = new PdfPTable(2);
					parrafo = new Paragraph();
					frase = new Phrase(texto.toString(), fuente);
					parrafo.add(frase);
					cell.setBackgroundColor(Color.GRAY);
					cell.setPhrase(new Phrase("NOMBRE", font));
					tabla.addCell(cell);
					cell.setPhrase(new Phrase("EMAIL", font));
					tabla.addCell(cell);
					cell.setBackgroundColor(Color.WHITE);
				}
			}

			parrafo.add(tabla);

			document.add(parrafo);

			// Obtengo los traslados

			PdfUtil.nuevaPagina(document, true, true, true, PageSize.A4);

			collectionPart = ParticipantesUtil.getParticipantes(cct,
					numExp, "(ROL != 'TRAS' OR ROL IS NULL)",
					"NOMBRE");
			itPart = collectionPart.iterator();

			texto = new StringBuilder("\n\n\n\n\n");
			texto.append("Participantes Interesados Exp. " + numExp + ": " + collectionPart.toList().size());
			frase = new Phrase(texto.toString(), fuente);

			tabla = new PdfPTable(2);
			float[] medidaCeldas = { 0.55f, 2.25f };
			tabla.setWidths(medidaCeldas);
			parrafo = new Paragraph();

			parrafo.add(frase);
			cell.setBackgroundColor(Color.GRAY);
			cell.setPhrase(new Phrase("DNI", font));
			tabla.addCell(cell);
			cell.setPhrase(new Phrase("NOMBRE", font));
			tabla.addCell(cell);

			cell.setBackgroundColor(Color.WHITE);
			contFilas = 0;
			while (itPart.hasNext()) {
				IItem participante = itPart.next();
				cell.setPhrase(new Phrase(participante.getString("NDOC")));
				tabla.addCell(cell);
				cell.setPhrase(new Phrase(participante.getString("NOMBRE")));
				tabla.addCell(cell);
				contFilas++;
				if (contFilas == 34) {
					contFilas = 0;
					parrafo.add(tabla);
					document.add(parrafo);
					PdfUtil.nuevaPagina(document, true, true, true,
							PageSize.A4);
					tabla = new PdfPTable(2);
					tabla.setWidths(medidaCeldas);
					parrafo = new Paragraph();
					frase = new Phrase(texto.toString(), fuente);
					parrafo.add(frase);
					cell.setBackgroundColor(Color.GRAY);
					cell.setPhrase(new Phrase("DNI", font));
					tabla.addCell(cell);
					cell.setPhrase(new Phrase("NOMBRE", font));
					tabla.addCell(cell);
					cell.setBackgroundColor(Color.WHITE);
				}
			}

			parrafo.add(tabla);

			document.add(parrafo);

			document.close();

			int idTpdocNot = DocumentosUtil.getIdTipoDocByCodigo(cct,
					"Inf-Not-Tras");
			String sTpdoc = DocumentosUtil.getTipoDocNombreByCodigo(cct,
					"Inf-Not-Tras");
			IItem entityDocument = DocumentosUtil.generaYAnexaDocumento(cct,
					codTramiteCreac, idTpdocNot, sTpdoc, ffilePathDoc,
					Constants._EXTENSION_PDF);
			entityDocument.store(cct);

		} catch (ISPACException e) {
			LOGGER.error(e.getMessage(), e);
			throw new ISPACRuleException("Error. ", e);
		} catch (FileNotFoundException e) {
			LOGGER.error(e.getMessage(), e);
			throw new ISPACRuleException("Error. ", e);
		} catch (DocumentException e) {
			LOGGER.error(e.getMessage(), e);
			throw new ISPACRuleException("Error. ", e);
		} catch (MalformedURLException e) {
			LOGGER.error(e.getMessage(), e);
			throw new ISPACRuleException("Error. ", e);
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
			throw new ISPACRuleException("Error. ", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static boolean comprueboFirmadoDocumentoConCircuitoFirmaEspecificoAnterior(
			IRuleContext rulectx) throws ISPACRuleException {
		/**
		 * Comprobar antes de generar los documentos y mandarlos a firmar que en
		 * el tr�mite en el que est� se ha firmado el documento con el circuito
		 * espec�fico y no con otro y si con otro no se generar� automaticamente
		 * el siguiente tr�mite y no lo mandar� a firmar.
		 * 
		 * **/
		boolean firmadoCircuitoEspecifico = false;
		try {
			/****************************************************************/
			// APIs
			IClientContext cct = rulectx.getClientContext();
			/******************************************************************/

			IItemCollection col = TramitesUtil.getTramites(cct,
					rulectx.getNumExp(), "FECHA_CIERRE IS NOT NULL",
					"FECHA_CIERRE DESC");
			Iterator<IItem> itCollection = col.iterator();
			if (itCollection.hasNext()) {
				IItem itemTramite = itCollection.next();
				String sQuery = "(ID_TRAMITE="
						+ itemTramite.getInt("ID_TRAM_EXP")
						+ " AND NOMBRE='Decreto')";
				IItemCollection itColDocTramite = DocumentosUtil.getDocumentos(
						cct, rulectx.getNumExp(), sQuery, "");
				Iterator<IItem> itDocTramite = itColDocTramite.iterator();
				if (itDocTramite.hasNext()) {
					IItem docFirma = itDocTramite.next();
					int idDoc = docFirma.getInt("ID");
					firmadoCircuitoEspecifico = CircuitosUtil
							.firmadoDocumentoConCircuitoFirmaEspecifico(
									rulectx, idDoc,
									itemTramite.getInt("ID_TRAM_PCD"));
				}
			}

		} catch (ISPACException e) {
			LOGGER.error("Error al comprobar el doc: " + rulectx.getNumExp()
					+ ". " + e.getMessage(), e);
			throw new ISPACRuleException("Error al comprobar el doc: "
					+ rulectx.getNumExp() + ". " + e.getMessage(), e);
		}
		return firmadoCircuitoEspecifico;
	}

	@SuppressWarnings("unchecked")
	public static void setAutomatizacion(IRuleContext rulectx, boolean valor) throws ISPACRuleException {
		try {
			// *********************************************
			IClientContext cct = rulectx.getClientContext();
			IInvesflowAPI invesFlowAPI = cct.getAPI();
			IEntitiesAPI entitiesAPI = invesFlowAPI.getEntitiesAPI();
			// *********************************************

			IItemCollection colDecreto = entitiesAPI.getEntities("SGD_DECRETO", rulectx.getNumExp());
			Iterator<IItem> iterDecreto = colDecreto.iterator();
			IItem decreto = null;
			if (iterDecreto.hasNext()) {
				decreto = iterDecreto.next();

			} else {
				decreto = entitiesAPI.createEntity("SGD_DECRETO", rulectx.getNumExp());

			}
			String auto = "NO";
			if (valor) {
				auto = "SI";
			}
			decreto.set("AUTOMATIZACION", auto);
			decreto.store(cct);

		} catch (Exception e) {
			LOGGER.error( "Error al generar el nuevo documento en el expediente: " + rulectx.getNumExp() + ". " + e.getMessage(), e);
			throw new ISPACRuleException( "Error al generar el nuevo documento en el expediente: " + rulectx.getNumExp() + ". " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	public static boolean decretoAutomatizacion(IRuleContext rulectx) throws ISPACRuleException {
		boolean automatizacion = false;

		try {
			// *********************************************
			IClientContext cct = rulectx.getClientContext();
			IInvesflowAPI invesFlowAPI = cct.getAPI();
			IEntitiesAPI entitiesAPI = invesFlowAPI.getEntitiesAPI();
			// *********************************************
			IItemCollection colDecreto = entitiesAPI.getEntities("SGD_DECRETO", rulectx.getNumExp());
			Iterator<IItem> iterDecreto = colDecreto.iterator();
			if (iterDecreto.hasNext()) {
				IItem itemDecre = iterDecreto.next();
				String automati = itemDecre.getString("AUTOMATIZACION");
				if ("SI".equals(automati)) {
					automatizacion = true;
				}
			}

		} catch (Exception e) {
			LOGGER.error( "Error al generar el nuevo documento en el expediente: " + rulectx.getNumExp() + ". " + e.getMessage(), e);
			throw new ISPACRuleException( "Error al generar el nuevo documento en el expediente: " + rulectx.getNumExp() + ". " + e.getMessage(), e);
		}
		return automatizacion;
	}

	@SuppressWarnings("unchecked")
	public static void mandarAfirmarCircuitoFirmaEspecificoTodosDocumentosTramite(
			IRuleContext rulectx) throws ISPACRuleException {
		try {
			// *********************************************
			IClientContext cct = rulectx.getClientContext();
			// *********************************************
			if (decretoAutomatizacion(rulectx)) {
				if (comprueboFirmadoDocumentoConCircuitoFirmaEspecificoAnterior(rulectx)) {
					/**
					 * Regla que envia todos los documentos del tr�mite a firmar
					 * al circuito de firma que se haya especificado en el
					 * tr�mite.
					 * **/
					IItemCollection itColDocTramite = DocumentosUtil
							.getDocumentos(cct, rulectx.getNumExp(),
									"(ID_TRAMITE=" + rulectx.getTaskId() + ")",
									"");
					Iterator<IItem> itDocTramite = itColDocTramite.iterator();
					while (itDocTramite.hasNext()) {
						IItem docFirma = itDocTramite.next();
						CircuitosUtil.iniciarCircuitoTramite(rulectx,
								docFirma.getInt("ID"));
					}
				} else {
					LOGGER.warn("El circuito de firma del tr�mite espec�fico es diferente a quien a firmado el documento de decreto");
				}
			} else {
				LOGGER.warn("No acepta la automatizaci�n");
			}
		} catch (Exception e) {
			LOGGER.error(
					"Error al generar el nuevo documento en el expediente: "
							+ rulectx.getNumExp() + ". " + e.getMessage(), e);
			throw new ISPACRuleException(
					"Error al generar el nuevo documento en el expediente: "
							+ rulectx.getNumExp() + ". " + e.getMessage(), e);
		}
	}

	public static String getNumDecreto(IRuleContext rulectx) throws ISPACRuleException {
		
		String sNumDecreto = "";
		int iNumDec = getNumDecreto(rulectx.getClientContext(), rulectx.getNumExp());
		
		if(Integer.MIN_VALUE < iNumDec){
			sNumDecreto = Integer.toString(iNumDec);
		}
		
		return sNumDecreto;
	}
	
	public static String getCampoSGDDecreto(IClientContext cct, String numexp, String campo) throws ISPACRuleException {
		String valor = "";
		
		try {
			IItem itemDecre = DecretosUtil.getDecreto(cct, numexp);
			
			if(null != itemDecre){
				valor = itemDecre.getString(campo);
			}

		} catch (Exception e) {
			LOGGER.error( "Error al obtener el campo: " + campo + " de SGD_DECRETO del expediente: " + numexp + ". " + e.getMessage(), e);
			throw new ISPACRuleException( "Error al obtener el campo: " + campo + " de SGD_DECRETO del expediente: " + numexp + ". " + e.getMessage(), e);
		}
		
		return valor;
	}
	
	public static int getNumDecreto(IClientContext cct, String numexp) throws ISPACRuleException {

		int numDec = Integer.MIN_VALUE;
		
		String valor = DecretosUtil.getCampoSGDDecreto(cct, numexp, DecretoTabla.NUMERO_DECRETO);

		if(StringUtils.isNotEmpty(valor)){
			numDec = Integer.parseInt(valor);
		}
		
		return numDec;
	}
	
	public static String getAnioDecreto(IClientContext cct, String numexp) throws ISPACRuleException {
		String anioDec = "";

		String valor = DecretosUtil.getCampoSGDDecreto(cct, numexp, DecretoTabla.ANIO);

		if(StringUtils.isNotEmpty(valor)){
			anioDec = valor;
		}
		
		return anioDec;
	}
	
	public static String getNumeroDecretoCompleto(IClientContext cct, String numexp) throws ISPACRuleException {
		String numDec = "";

		String anio = DecretosUtil.getCampoSGDDecreto(cct, numexp, DecretoTabla.ANIO);
		String numDecreto = DecretosUtil.getCampoSGDDecreto(cct, numexp, DecretoTabla.NUMERO_DECRETO);

		if(StringUtils.isNotEmpty(anio)){
			numDec = anio;
		}
		
		numDec += "/";
		
		if(StringUtils.isNotEmpty(numDecreto)){
			numDec += numDecreto;
		}
		
		return numDec;
	}
	
	public static Date getFechaDecreto(IClientContext cct, String numexp) throws ISPACRuleException {
		Date fechaDec = new Date();

		try {
			IItem itemDecre = DecretosUtil.getDecreto(cct, numexp);
			
			if(null != itemDecre){
				fechaDec = itemDecre.getDate(DecretoTabla.FECHA_DECRETO);
			}

		} catch (Exception e) {
			LOGGER.error( "Error al obtener el extracto del decreto: " + numexp + ". " + e.getMessage(), e);
			throw new ISPACRuleException( "Error al obtener el extracto del decreto: " + numexp + ". " + e.getMessage(), e);
		}
		return fechaDec;
	}
	
	public static String getExtractoDecreto(IClientContext cct, String numexp) throws ISPACRuleException {
		String extractoDec = "";

		String valor = DecretosUtil.getCampoSGDDecreto(cct, numexp, DecretoTabla.EXTRACTO_DECRETO);

		if(StringUtils.isNotEmpty(valor)){
			extractoDec = valor;
		}
		
		return extractoDec;
	}
}
