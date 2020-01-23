package ieci.tecdoc.sgm.consolidacion.mgr;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.ieci.tecdoc.common.entity.dao.DBEntityDAOFactory;
import com.ieci.tecdoc.common.repository.helper.ISRepositoryDocumentHelper;
import com.ieci.tecdoc.common.repository.vo.ISRepositoryRetrieveDocumentVO;

import ieci.tecdoc.sgm.consolidacion.config.ConfigLoader;
import ieci.tecdoc.sgm.consolidacion.config.ConsolidacionConfig;
import ieci.tecdoc.sgm.core.exception.SigemException;
import ieci.tecdoc.sgm.core.services.LocalizadorServicios;
import ieci.tecdoc.sgm.core.services.dto.Entidad;
import ieci.tecdoc.sgm.core.services.mensajes_cortos.MensajesCortosException;
import ieci.tecdoc.sgm.core.services.mensajes_cortos.ServicioMensajesCortos;
import ieci.tecdoc.sgm.core.services.registro.Document;
import ieci.tecdoc.sgm.core.services.registro.DocumentInfo;
import ieci.tecdoc.sgm.core.services.registro.FieldInfo;
import ieci.tecdoc.sgm.core.services.registro.Page;
import ieci.tecdoc.sgm.core.services.registro.PersonInfo;
import ieci.tecdoc.sgm.core.services.registro.RegisterInfo;
import ieci.tecdoc.sgm.core.services.registro.RegisterWithPagesInfoPersonInfo;
import ieci.tecdoc.sgm.core.services.registro.ServicioRegistro;
import ieci.tecdoc.sgm.core.services.registro.UserInfo;
import ieci.tecdoc.sgm.core.services.repositorio.ContenedorDocumento;
import ieci.tecdoc.sgm.core.services.repositorio.ServicioRepositorioDocumentosTramitacion;
import ieci.tecdoc.sgm.core.services.telematico.Registro;
import ieci.tecdoc.sgm.core.services.telematico.RegistroConsulta;
import ieci.tecdoc.sgm.core.services.telematico.RegistroDocumento;
import ieci.tecdoc.sgm.core.services.telematico.RegistroDocumentos;
import ieci.tecdoc.sgm.core.services.telematico.RegistroEstado;
import ieci.tecdoc.sgm.core.services.telematico.Registros;
import ieci.tecdoc.sgm.core.services.telematico.ServicioRegistroTelematico;
import ieci.tecdoc.sgm.registropresencial.utils.RBUtil;

public class ConsolidacionManagerImpl implements ConsolidacionManager {

	/**
	 * Logger de la clase.
	 */
	private static final Logger logger = Logger.getLogger(ConsolidacionManagerImpl.class);

	/**
	 * Estado de registro presencial: COMPLETO
	 */
	private static final String AXSF_COMPLETE_STATE = "0";

	/**
	 * Servicio de gesti�n del Registro Telem�tico.
	 */
	private ServicioRegistroTelematico servicioRegistroTelem�tico = null;

	/**
	 * Servicio de gesti�n de documentos del Registro Telem�tico.
	 */
	private ServicioRepositorioDocumentosTramitacion servicioRde = null;

	/**
	 * Servicio de gesti�n del Registro Presencial.
	 */
	private ServicioRegistro servicioRegistro = null;

	/**
	 * Configuraci�n con soporte multientidad.
	 */
	protected ConsolidacionConfig config=new ConsolidacionConfig();
	
	//[Ticket 1014 Teresa]
	/**
	 * Servicio de Repositorio de documentos tramitaci�n
	 * **/
	private ServicioRepositorioDocumentosTramitacion servicioDocumentos = null;
	

	/**
	 * Locale de usuario presencial
	 */
	private Locale registryUserLocale = null;
	private SimpleDateFormat longFormatter = null;
	private SimpleDateFormat shortFormatter = null;

	/**
	 * Constructor.
	 * 
	 * @throws SigemException
	 *             si ocurre alg�n error.
	 */
	public ConsolidacionManagerImpl() throws SigemException {
		super();

		this.servicioRegistroTelem�tico = LocalizadorServicios.getServicioRegistroTelematico();
		this.servicioRegistro = LocalizadorServicios.getServicioRegistro();
		this.servicioRde = LocalizadorServicios.getServicioRepositorioDocumentosTramitacion();
		//[Ticket 1014 Teresa]
		this.servicioDocumentos = LocalizadorServicios.getServicioRepositorioDocumentosTramitacion();


		// Formateador de fechas en formato largo
		longFormatter = new SimpleDateFormat(RBUtil.getInstance(null).getProperty(
				ieci.tecdoc.sgm.registropresencial.utils.Keys.I18N_DATE_LONGFORMAT));
		longFormatter.setLenient(false);

		// Formateador de fechas en formato corto
		shortFormatter = new SimpleDateFormat(RBUtil.getInstance(null).getProperty(
				ieci.tecdoc.sgm.registropresencial.utils.Keys.I18N_DATE_SHORTFORMAT));
		shortFormatter.setLenient(false);
	}

	/**
	 * Realiza el proceso de consolidaci�n sobre una entidad.
	 * 
	 * @param idEntidad
	 *            Identificador de la entidad.
	 * @throws SigemException
	 *             si ocurre alg�n error.
	 */
	public void consolidaEntidad(String idEntidad) throws SigemException {

		try {
			logger.info("Comienzo del proceso de consolidaci�n en la entidad " + idEntidad);

			// Informaci�n de la entidad
			Entidad entidad = new Entidad();
			entidad.setIdentificador(idEntidad);

			// Criterios de b�squeda
			RegistroConsulta criteria = new RegistroConsulta();
			criteria.setStatus(RegistroEstado.STATUS_NOT_CONSOLIDATED);

			// Obtener los registros telem�ticos a consolidar
			Registros registros = servicioRegistroTelem�tico.query(null, criteria, entidad);
			if (logger.isInfoEnabled()) {
				logger.info(registros.count()
						+ " registro/s telem�tico/s encontrado/s para la consolidaci�n.");
			}

			// Procesar cada registro telem�tico
			for (int i = 0; i < registros.count(); i++) {
				consolidaRegistro(registros.get(i), entidad);
			}

			if (logger.isInfoEnabled()) {
				logger.info("Proceso de consolidaci�n finalizado en entidad " + idEntidad);
			}
		} catch (Throwable t) {
			logger.error("Error en la consolidaci�n de la entidad " + idEntidad, t);
		}
	}

	/**
	 * Consolida un registro telem�tico.
	 * 
	 * @param registro
	 *            Informaci�n del registro telem�tico.
	 * @param entidad
	 *            Informaci�n de la entidad.
	 * @throws SigemException
	 *             si ocurre alg�n error.
	 */
	protected void consolidaRegistro(Registro registro, Entidad entidad) throws SigemException {

		// TODO: Modificar este m�todo para enviar e-mail si ocurre excepci�n
		try {
			if (logger.isInfoEnabled()) {
				logger.info("Inicio del proceso de consolidaci�n del registro ["
						+ registro.getRegistryNumber() + "] en la entidad ["
						+ entidad.getIdentificador() + "]");
			}

			// Obtener los ficheros del registro telem�tico
			RegistroDocumentos documentos = servicioRegistroTelem�tico.obtenerDocumentosRegistro(
					registro.getRegistryNumber(), entidad);
			if (logger.isInfoEnabled()) {
				logger.info("El registro telem�tico " + registro.getRegistryNumber() + " tiene "
						+ documentos.count() + " documento/s");
			}
			String idEntidad=entidad.getIdentificador();
			cargarConfiguracion(idEntidad);
			
			// Informaci�n del usuario de registro presencial
			UserInfo userInfo = new UserInfo();
			userInfo.setUserName(config.getUserName());
			userInfo.setPassword(config.getPassword());

			if (userInfo.getLocale() == null) {
				userInfo.setLocale(Locale.getDefault());
			} else {
				userInfo.setLocale(registryUserLocale);
			}

			// Informaci�n de los intervinientes
			PersonInfo[] personInfos = getPersons(registro,idEntidad);

			// Consolidar el registro presencial
			RegisterInfo regInfo = servicioRegistro.consolidateFolder(userInfo,
					new Integer(config.getBookId()), getFolderAttributes(registro, personInfos,idEntidad), personInfos,
					getDocuments(documentos, entidad), entidad);

			if (logger.isInfoEnabled()) {
				logger.info("Registro presencial creado correctamente: " + regInfo.getNumber());
			}

			// Cambiar el estado del registro telem�tico de no consolidado a
			// consolidado
			servicioRegistroTelem�tico.establecerEstadoRegistro(registro.getRegistryNumber(),
					RegistroEstado.STATUS_CONSOLIDATED, entidad);
			
			/**
			 * [Ticket 1014 Teresa] Eliminar de la tabla sgmrdedocumentos el
			 * campo contenido y a�adir el identificador del documento que se
			 * obtiene del repositorio documental a1pageh -> fileid que luego se
			 * utiliza en la tabla ivolfilehdr
			 * **/
			// [**1]Obtengo los id de cada uno de los documentos en el registro
			// presencial
			RegisterWithPagesInfoPersonInfo inforRegPresencial = servicioRegistro
					.getInputRegister(userInfo, regInfo.getNumber(), entidad);

			Document[] docRegistroPresencial = inforRegPresencial.getDocInfo();
			for (int docRegPresen = 0; docRegPresen < docRegistroPresencial.length; docRegPresen++) {
				Document docReg = docRegistroPresencial[docRegPresen];
				// identificador del documento.

				List listadoInformacion = docReg.getPages();
	        	for (Iterator it2 = listadoInformacion.iterator(); it2.hasNext();) {
					Page page = (Page) it2.next();
					
					logger.info("- Registro Presencial:");
		        	logger.info("Identificador del documento. "+docReg.getDocID());
		        	logger.info("Identificador del fichero en registo. "+page.getFileID());
		        	logger.info("Nombre del documento. "+docReg.getDocumentName());
		        	logger.info("N�mero registro. "+regInfo.getNumber());
		        	RegistroDocumento docRegTelema = servicioRegistroTelem�tico.obtenerDocumentoRegistro("", registro.getRegistryNumber(), docReg.getDocumentName(), entidad);
		        	
		        	Integer bookID = Integer.parseInt(docReg.getBookId());
		        	Integer regId = Integer.parseInt(docReg.getFolderId());
		        	Integer pageId = Integer.parseInt(page.getPageID());
					//[Ruben #545416] Obtengo el id de Alfresco para poder incluirlo al borrar los docs de Telematico 
		        	String docUID = DBEntityDAOFactory.getCurrentDBEntityDAO().getDocUID(bookID, regId,pageId, entidad.getIdentificador());
		        	
					// [Josemi #545742] Correccion por error en las trazas del scheduler. No se pasaba bien la entidad.
					ISRepositoryRetrieveDocumentVO findVO = ISRepositoryDocumentHelper.getRepositoryRetrieveDocumentVO(bookID, regId, pageId, entidad.getIdentificador(), true);
					logger.warn("- Registro Telem�tico:");

//	        		logger.warn("guid: "+findVO.getGuid());
	        		logger.warn("Nombre: "+docRegTelema.getCode());	        	
		        	//[**2]Almacenar ese identificador en la tabla sgmrdedocumentos. 
					
		        	//Hay que crear un servicio web que elimine el contenido y a�ada el identificador.
					//[Ruben #545416] grabo el id de Alfresco al borrar de Telematico para funcionalidades que lo usan como recuperar por CSV
	        		servicioDocumentos.insertarIdFileBorrarContenido(docRegTelema.getGuid(), docUID, entidad);
	        		logger.warn("MODIFICADO ");
	        	}
			}

			/**
			 * [Ticket 1014 Teresa] FIN
			 * **/

			if (logger.isInfoEnabled()) {
				logger.info("Fin del proceso de consolidaci�n del registro ["
						+ registro.getRegistryNumber() + "] en la entidad ["
						+ entidad.getIdentificador() + "]");
			}
		} catch (Throwable t) {
			
			String message = "Error en la consolidaci�n del registro " + registro.getRegistryNumber()
					+ " de la entidad " + entidad.getIdentificador();
			
			
			logger.error(message, t);

			// Se env�a el e-mail de error en la consolidaci�n
			sendEmailErrorConsolidacion(message, t);
		}
	}

	private void sendEmailErrorConsolidacion(String message, Throwable t) throws SigemException,
			MensajesCortosException {
		//Nota: El objeto config ya se encuentra cargado (Este metodo es privado, implica que se ha cargado desde uno p�blico).
		if (!isBlank(config.getErrorEnvioEmailOrigen()) && 
			!isBlank(config.getErrorEnvioEmailDestino()) && 
			!isBlank(config.getErrorEnvioEmailAsunto())) {

			StringBuffer content = new StringBuffer();
			content.append(message+"\n");
			content.append("Excepci�n: \n");
			content.append(t.getMessage()+"\n");
			content.append(t.getCause());
			content.append(getStackTrace(t));
			
			if (logger.isInfoEnabled()) {
				logger.info("Se procede a enviar la notificaci�n del error en la consolidaci�n del registro.\n E-mail Origen: "
						+ config.getErrorEnvioEmailOrigen()
						+ "\n E-mail Destino: "
						+ config.getErrorEnvioEmailDestino()
						+ "\n Asunto E-mail: "
						+ config.getErrorEnvioEmailAsunto()
						+ "\n Contenido E-mail: "
						+content.toString());
			}
			// Enviar el e-mail de error de consolidaci�n
			ServicioMensajesCortos servicioMensajesCortos = LocalizadorServicios
					.getServicioMensajesCortos();

			String[] emailDestinoArray = {config.getErrorEnvioEmailDestino()};
			
			servicioMensajesCortos.sendMail(config.getErrorEnvioEmailOrigen(), 
					emailDestinoArray, null, null,
					config.getErrorEnvioEmailAsunto(), content.toString(), null);
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("No se env�a el e-mail de notificaci�n del error en la consolidaci�n del registro porque as� se ha configurado al dejar vac�os las propiedades emailOrigen, emailDestino y emailAsunto");
			}
		}
	}
	
	public boolean isBlank(String cadena){
		if (cadena == null || cadena.equals(""))
			return true;
		return false;
	}

	protected FieldInfo[] getFolderAttributes(Registro registro, PersonInfo[] personInfos,String idEntidad) {

		// Nombre del interviniente principal
		String personName = null;
		if (!ArrayUtils.isEmpty(personInfos)) {
			personName = personInfos[0].getPersonName();
		}

		// Comprobaci�n de la informaci�n adicional
		String additionalInfo = null;
		if (registro.getAdditionalInfo() != null) {
			additionalInfo = new String(registro.getAdditionalInfo());
		}

		// Comprobaci�n de la fecha efectiva
		Date effectiveDate = registro.getEffectiveDate();
		if (effectiveDate == null) {
			effectiveDate = registro.getRegistryDate();
		}

		cargarConfiguracion(idEntidad);
		// C�digo de asunto del registro
		String topic = registro.getTopic();
		if (StringUtils.isBlank(topic)) {
			topic = config.getDefaultCACode();
		}

		/*
		 * fld1 => N�mero de registro fld2 => Fecha de registro fld3 => Usuario
		 * fld4 => Fecha de trabajo fld5 => Oficina de registro fld6 => Estado
		 * fld7 => Origen fld8 => Destino fld9 => Remitentes fld10 => N�.
		 * registro original fld11 => Tipo de registro original fld12 => Fecha
		 * de registro original fld13 => Registro original fld14 => Tipo de
		 * transporte fld15 => N�mero de transporte fld16 => Tipo de asunto
		 * fld17 => Resumen fld18 => Comentario fld19 => Referencia de
		 * Expediente fld20 => Fecha del documento 506 => No acompa�a documentaci�n f�sica ni otros soportes
		 */
		return new FieldInfo[] {
				getFieldInfo("1", registro.getRegistryNumber()),
				getFieldInfo("2", (effectiveDate != null ? longFormatter.format(effectiveDate) : "")),
				getFieldInfo("3", config.getUserName()),
				getFieldInfo("4", (registro.getRegistryDate() != null ? shortFormatter.format(registro.getRegistryDate()) : "")),
				getFieldInfo("5", registro.getOficina()), 
				getFieldInfo("6", AXSF_COMPLETE_STATE),				
				getFieldInfo("8", registro.getAddressee()), 
				getFieldInfo("9", personName),
				getFieldInfo("16", topic), 
				getFieldInfo("17", registro.getTopic()),
				getFieldInfo("18", additionalInfo),
				getFieldInfo("19", registro.getNumeroExpediente()),
				getFieldInfo("506", "1")};
	}

	protected PersonInfo[] getPersons(Registro registro,String idEntidad) {
		PersonInfo[] persons = null;

		if (registro != null) {

			// Componer el nombre del interviniente
			String personName = StringUtils.trim(StringUtils.defaultString(registro.getSenderId(),
					"") + " " + StringUtils.defaultString(registro.getName(), ""));

			cargarConfiguracion(idEntidad);
			if (personName.length() > config.getMaxLength()) {

				String person1Name = StringUtils.trim(StringUtils.substringBeforeLast(
						StringUtils.substring(personName, 0, config.getMaxLength()), " "));
				String person2Name = StringUtils.trim(StringUtils.substringAfterLast(personName,
						person1Name));

				persons = new PersonInfo[] { getPersonInfo(person1Name), getPersonInfo(person2Name) };

			} else {
				persons = new PersonInfo[] { getPersonInfo(personName) };
			}
		}

		return persons;
	}

	protected DocumentInfo[] getDocuments(RegistroDocumentos documentos, Entidad entidad)
			throws SigemException {

		DocumentInfo[] docs = new DocumentInfo[documentos.count()];

		for (int i = 0; i < documentos.count(); i++) {

			// Informaci�n del documento en el registro telem�tico
			RegistroDocumento documento = documentos.get(i);

			// Informaci�n del contenedor del documento
			ContenedorDocumento contenedor = servicioRde.retrieveDocumentInfo("",
					documento.getGuid(), entidad);

			// Informaci�n del documento en el registro presencial
			docs[i] = new DocumentInfo();
			docs[i].setDocumentName(documento.getCode());
			docs[i].setExtension(contenedor.getExtension().toLowerCase());
			docs[i].setDocumentContent(contenedor.getContent());
			docs[i].setFileName(documento.getRegistryNumber() + "_" + documento.getCode() + "_" + i);
			docs[i].setPageName(documento.getCode() + "." + contenedor.getExtension().toLowerCase());
		}

		return docs;
	}

	protected static PersonInfo getPersonInfo(String personName) {
		PersonInfo person = new PersonInfo();
		person.setPersonName(personName);
		// person.setPersonId(null);
		// person.setDomId(null);
		// person.setDirection(null);
		return person;
	}

	protected static FieldInfo getFieldInfo(String fieldId, String value) {
		FieldInfo fieldInfo = new FieldInfo();
		fieldInfo.setFieldId(fieldId);
		fieldInfo.setValue(value);
		return fieldInfo;
	}
	
	/**
	 * Devuelve la pila de la traza de la excepci�n como un string
	 * @param aThrowable Excepci�n
	 * @return	La pila de la traza de la excepci�n como un string
	 */
	 private String getStackTrace(Throwable aThrowable) {
		    final Writer result = new StringWriter();
		    final PrintWriter printWriter = new PrintWriter(result);
		    aThrowable.printStackTrace(printWriter);
		    return result.toString();
		  }
	 
	 private void cargarConfiguracion(String idEntidad){
		 if(idEntidad==null ) return;
		 config=ConfigLoader.getInstance().getConfig(idEntidad);
	 }
}
