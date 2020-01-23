package ieci.tdw.ispac.api.expedients;

import ieci.tdw.ispac.api.IEntitiesAPI;
import ieci.tdw.ispac.api.IGenDocAPI;
import ieci.tdw.ispac.api.IInboxAPI;
import ieci.tdw.ispac.api.IInvesflowAPI;
import ieci.tdw.ispac.api.IProcedureAPI;
import ieci.tdw.ispac.api.IRegisterAPI;
import ieci.tdw.ispac.api.ISearchAPI;
import ieci.tdw.ispac.api.ITXTransaction;
import ieci.tdw.ispac.api.IThirdPartyAPI;
import ieci.tdw.ispac.api.entities.SpacEntities;
import ieci.tdw.ispac.api.errors.ISPACException;
import ieci.tdw.ispac.api.expedients.adapter.Constants;
import ieci.tdw.ispac.api.expedients.adapter.Procedure;
import ieci.tdw.ispac.api.expedients.adapter.Table;
import ieci.tdw.ispac.api.expedients.adapter.XMLFacade;
import ieci.tdw.ispac.api.impl.InvesflowAPI;
import ieci.tdw.ispac.api.item.IItem;
import ieci.tdw.ispac.api.item.IItemCollection;
import ieci.tdw.ispac.api.item.IProcedure;
import ieci.tdw.ispac.api.item.IProcess;
import ieci.tdw.ispac.api.item.IResponsible;
import ieci.tdw.ispac.api.item.ITask;
import ieci.tdw.ispac.api.rule.EventManager;
import ieci.tdw.ispac.api.rule.EventsDefines;
import ieci.tdw.ispac.ispaclib.bean.CollectionBean;
import ieci.tdw.ispac.ispaclib.context.ClientContext;
import ieci.tdw.ispac.ispaclib.context.StateContext;
import ieci.tdw.ispac.ispaclib.notices.Notices;
import ieci.tdw.ispac.ispaclib.resp.RespFactory;
import ieci.tdw.ispac.ispaclib.resp.Responsible;
import ieci.tdw.ispac.ispaclib.search.objects.impl.SearchDomain;
import ieci.tdw.ispac.ispaclib.search.objects.impl.SearchInfo;
import ieci.tdw.ispac.ispaclib.search.vo.SearchResultVO;
import ieci.tdw.ispac.ispaclib.sicres.RegisterHelper;
import ieci.tdw.ispac.ispaclib.sicres.vo.Annexe;
import ieci.tdw.ispac.ispaclib.sicres.vo.Intray;
import ieci.tdw.ispac.ispaclib.sicres.vo.RegisterInfo;
import ieci.tdw.ispac.ispaclib.sicres.vo.RegisterType;
import ieci.tdw.ispac.ispaclib.sicres.vo.ThirdPerson;
import ieci.tdw.ispac.ispaclib.thirdparty.IThirdPartyAdapter;
import ieci.tdw.ispac.ispaclib.util.FileTemporaryManager;
import ieci.tdw.ispac.ispaclib.util.ISPACConfiguration;
import ieci.tdw.ispac.ispaclib.utils.ArrayUtils;
import ieci.tdw.ispac.ispaclib.utils.CollectionUtils;
import ieci.tdw.ispac.ispaclib.utils.DBUtil;
import ieci.tdw.ispac.ispaclib.utils.MimetypeMapping;
import ieci.tdw.ispac.ispaclib.utils.StringUtils;
import ieci.tdw.ispac.ispaclib.utils.TypeConverter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.NodeIterator;

import es.dipucr.sigem.api.rule.common.utils.ExpedientesUtil;

public class Expedients {

	/** Logger de la clase. */
	private static final Logger LOGGER = Logger.getLogger(Expedients.class);

	private ClientContext mcontext;

	private static final String DOC_ORIGIN_TELEMATIC 	= "REGISTRO TELEM�TICO";
	private static final String DOC_ORIGIN_PRESENTIAL	= "REGISTRO PRESENCIAL";
	private static final String DOC_REG_TYPE			= "ENTRADA";

    public Expedients() {
    	this(null);
    }

    public Expedients(ClientContext context) {
    	if (context != null) {
    		mcontext = context;
    	} else {
    		mcontext = new ClientContext();
    		mcontext.setAPI(new InvesflowAPI(mcontext));
    	}
    }


    public String initExpedient(CommonData commonData,
		  	 String specificDataXML,
		  	 List<?> documents, String initSystem) throws ISPACException {

		IInvesflowAPI invesFlowAPI = mcontext.getAPI();
		IProcedureAPI procedureAPI = invesFlowAPI.getProcedureAPI();
		IEntitiesAPI entitiesAPI = invesFlowAPI.getEntitiesAPI();
		IGenDocAPI genDocAPI = invesFlowAPI.getGenDocAPI();

		// Lista de referencias de los documentos creados en el gestor documental
		// para eliminarlos en caso de error
		List<String> documentRefs = new ArrayList<String>();

		// Ejecuci�n en un contexto transaccional
		boolean ongoingTX = mcontext.ongoingTX();
		boolean bCommit = false;

		try {
	        // Abrir transacci�n para deshacer la creaci�n del expediente en caso de error
			if (!ongoingTX) {
				mcontext.beginTX();
			}

			// Obtener el procedimiento del cat�logo a iniciar (c�digo de procedimiento vigente)
			// IItem ctProcedure = getCatalogProcedure(entitiesAPI, commonData.getSubjectType());
			String pcdCode = commonData.getSubjectType();
			IItem procedure = procedureAPI.getProcedureByCode(pcdCode, IProcedure.PCD_STATE_CURRENT);
			if (procedure == null) {

				throw new ISPACException("C�digo de procedimiento vigente desconocido: " + pcdCode);
			}

			Responsible responsible = null;

			// Obtener el responsable del procedimiento
			// IProcedure procedure = invesFlowAPI.getProcedure(ctProcedure.getKeyInt());
			String respUID = procedure.getString("PPROCEDIMIENTOS:ID_RESP");
			if (StringUtils.isBlank(respUID)) {

				throw new ISPACException("El procedimiento no tiene ning�n responsable por defecto");
			}
			responsible = RespFactory.createResponsible(respUID);

//			}

			// Establecer el responsable para el expediente a iniciar
			mcontext.setUser(responsible);

			// Crear el proceso del expediente
			Map<String, String> params = new HashMap<String, String>();
			params.put("COD_PCD", procedure.getString("CTPROCEDIMIENTOS:COD_PCD"));
			params.put("OMITIR_EVENTO_TRAS_INICIAR", "true");
			params.put("specificDataXML", specificDataXML);

			int procedureId = procedure.getInt("CTPROCEDIMIENTOS:ID");
			int processId = invesFlowAPI.getTransactionAPI().createProcess(procedureId, params);

			// Obtener el expediente del procedimiento creado
			IProcess process = invesFlowAPI.getProcess(processId);
			String numexp = process.getString("NUMEXP");
			IItem expedient = entitiesAPI.getExpedient(numexp);

			// Inicia el contexto de ejecuci�n para que se ejecuten
            // las reglas asociadas a la entidad //////////////////
            StateContext stateContext = mcontext.getStateContext();
            if (stateContext == null) {
                stateContext = new StateContext();
                mcontext.setStateContext(stateContext);
            }
            stateContext.setPcdId(procedureId);
            stateContext.setProcessId(processId);
            stateContext.setNumexp(numexp);

			// Asunto del expediente
			expedient.set("ASUNTO", procedure.getString("CTPROCEDIMIENTOS:ASUNTO"));

			// Establecer los datos comunes del expediente
			if (!StringUtils.isEmpty(commonData.getRegisterNumber())) {

				expedient.set("NREG", commonData.getRegisterNumber());
				expedient.set("FREG", commonData.getRegisterDate());
			}

			// Procesar los interesados
			InterestedPerson interestedPrincipal = processInterested(entitiesAPI, commonData.getInterested(), numexp);

			if (interestedPrincipal != null) {

				expedient.set("IDTITULAR", interestedPrincipal.getThirdPartyId());
				expedient.set("NIFCIFTITULAR", interestedPrincipal.getNifcif());
				expedient.set("IDENTIDADTITULAR", interestedPrincipal.getName());
				expedient.set("DOMICILIO", interestedPrincipal.getPostalAddress());
				expedient.set("CPOSTAL", interestedPrincipal.getPostalCode());
				expedient.set("CIUDAD", interestedPrincipal.getPlaceCity());
				expedient.set("REGIONPAIS", interestedPrincipal.getRegionCountry());
				expedient.set("DIRECCIONTELEMATICA", interestedPrincipal.getTelematicAddress());
				expedient.set("TIPODIRECCIONINTERESADO", interestedPrincipal.getNotificationAddressType());
				expedient.set("TFNOFIJO", interestedPrincipal.getPhone());
				expedient.set("TFNOMOVIL", interestedPrincipal.getMobilePhone());
			}

			expedient.store(mcontext);

			// Procesar los datos espec�ficos del procedimiento
			processSpecificDataXML(entitiesAPI, specificDataXML, procedure, numexp);

			// Procesar los documentos
			processDocuments(entitiesAPI, genDocAPI, commonData, documents, numexp, documentRefs);

			// Generar un aviso en la bandeja de avisos electr�nicos
			String message = "notice.initExpedient";
			if (initSystem != null){
				message = "notice.initExpedient.externSystem";
			}

			Notices.generateNotice(mcontext, processId, 0, 0, numexp, message, mcontext.getAPI().getProcess(processId).getString("ID_RESP"), Notices.TIPO_AVISO_EXPEDIENTE_INICIADO_WS);

			// Contruir el contexto de ejecuci�n de eventos.
			// Incluir en el contexto de ejecucion de las reglas el XML con los datos de entrada
			params = new HashMap<String, String>();
			params.put("specificDataXML", specificDataXML);
			EventManager eventmgr = new EventManager(mcontext, params);
			eventmgr.newContext();
			eventmgr.getRuleContextBuilder().addContext(process);

			// Ejecutar evento de sistema al iniciar proceso.
			eventmgr.processSystemEvents(
					EventsDefines.EVENT_OBJ_PROCEDURE,
					EventsDefines.EVENT_EXEC_START_AFTER);

			// Ejecutar evento al iniciar proceso.
			eventmgr.processEvents(
					EventsDefines.EVENT_OBJ_PROCEDURE,
					procedure.getInt("CTPROCEDIMIENTOS:ID"),
					EventsDefines.EVENT_EXEC_START_AFTER);


			// Si todo ha sido correcto se hace commit de la transacci�n
			bCommit = true;

			return numexp;

		} catch (Exception e) {

			LOGGER.error("Error al iniciar expediente", e);

			// Si se produce alg�n error
			// se eliminan los ficheros subidos al gestor documental
			deleteAttachedFiles(genDocAPI, documentRefs);

			if (e instanceof ISPACException) {
				throw ( (ISPACException) e);
			} else {
				throw new ISPACException("Error Registro Telem�tico", e);
			}

		} finally {
			if (!ongoingTX) {
				mcontext.endTX(bCommit);
			}
		}
	}

    /**
     * Iniciar un expediente.
     *
     * @param commonData Datos comunes para todos los expedientes.
     * @param specificDataXML XML con los datos espec�ficos del expediente.
     * @param documents Lista de documentos asociados al expediente.
     * @return Cierto si el expediente se ha iniciado correctamente.
     * @throws ISPACException Si se produce alg�n error al iniciar el expediente.
     */
    public boolean initExpedient(CommonData commonData,
    						  	 String specificDataXML,
    						  	 List<?> documents) throws ISPACException {
    	String numExp = initExpedient(commonData, specificDataXML, documents, null);
    	return StringUtils.isNotEmpty(numExp);
	}

    /**
     * Cambia el estado administrativo de un expediente
     * @param numExp N�mero de expediente.
     * @param estadoAdm Nuevo estado administrativo
     * @return Cierto si la operaci�n se ha realizado con �xito, falso en caso contrario
     * @throws ISPACException
     */
    public boolean changeAdmState(String numExp, String admState) throws ISPACException{
		IInvesflowAPI invesFlowAPI = mcontext.getAPI();
		IEntitiesAPI entitiesAPI = invesFlowAPI.getEntitiesAPI();
		IItem itemExp = entitiesAPI.getExpedient(numExp);
		itemExp.set("ESTADOADM", admState);
		itemExp.store(mcontext);

		IItem itemProcess = invesFlowAPI.getProcess(numExp);
		String respUID = itemProcess.getString("ID_RESP");
		Responsible responsible = RespFactory.createResponsible(respUID);
		mcontext.setUser(responsible);
		// Generar un aviso en la bandeja de avisos electr�nicos
		String message = "notice.changeStateAdm";
		Notices.generateNotice(mcontext, itemExp.getInt("IDPROCESO"), 0, 0, numExp, message, mcontext.getAPI().getProcess(itemExp.getInt("IDPROCESO")).getString("ID_RESP"), Notices.TIPO_AVISO_EXP_CAMBIO_ESTADO_ADM_WS);

    	return true;
    }

    public boolean moveExpedientToStage(String numExp, String stageCtId) throws ISPACException{
		IInvesflowAPI invesFlowAPI = mcontext.getAPI();
		ITXTransaction tx = invesFlowAPI.getTransactionAPI();
		IProcedureAPI procedureAPI = invesFlowAPI.getProcedureAPI();

		IItem itemProcess = invesFlowAPI.getProcess(numExp);
		int pcdId = itemProcess.getInt("ID_PCD");
		int stagepcdid = 0;
		IItemCollection itemcol = procedureAPI.getStages(pcdId);
		while (itemcol.next()){
			IItem itemStage = itemcol.value();
			if  (itemStage.getInt("ID_CTFASE") == Integer.parseInt(stageCtId)){
				stagepcdid = itemStage.getKeyInt();
				break;
			}
		}
		if (stagepcdid == 0)
			throw new ISPACException("Fase NO encontrada en el procedimiento");
		try{
			tx.gotoStage(itemProcess.getKeyInt(), stagepcdid);

			itemcol = invesFlowAPI .getStagesProcess(itemProcess.getKeyInt());
			String respUID = null;
			while (itemcol.next()) {
				IItem itemStage = (IItem)itemcol.value();
				if (itemStage.getInt("ID_FASE") == stagepcdid){
					respUID = itemStage.getString("ID_RESP");
					break;
				}
			}

			Responsible responsible = RespFactory.createResponsible(respUID);
			mcontext.setUser(responsible);

			// Generar un aviso en la bandeja de avisos electr�nicos
			String message = "notice.moveExpedient";

			Notices.generateNotice(mcontext, itemProcess.getKeyInt(), 0, 0, numExp, message, mcontext.getAPI().getProcess(itemProcess.getKeyInt()).getString("ID_RESP"), Notices.TIPO_AVISO_EXP_REUBICADO_WS);

		}catch(ISPACException e){
			throw e;
		}
		return true;
    }

    /**
     * A�ade documentos al tr�mite de un expediente.
     * @param numExp N�mero de expediente.
     * @param regNum N�mero de registro de entrada.
     * @param regDate Fecha de registro de entrada.
     * @param documents Lista de documentos asociados al expediente.
     * @return Cierto si los documentos se han creado correctamente.
     * @throws ISPACException Si se produce alg�n error.
     */
    public boolean addDocuments(String numExp, String regNum, Date regDate, List<?> documents) throws ISPACException {

    	if (!CollectionUtils.isEmpty(documents)) {

			IInvesflowAPI invesFlowAPI = mcontext.getAPI();
			IGenDocAPI genDocAPI = invesFlowAPI.getGenDocAPI();
			
			//[eCenpri-Manu Ticket #303] - INICIO - ALSIGM3 Dar informaci�n de los documentos anexados a los expedientes.
			IItem expediente = invesFlowAPI.getEntitiesAPI().getExpedient(numExp);
			String asunto = "";
			if(null != expediente){
				asunto = expediente.getString("ASUNTO");
				if(StringUtils.isNotEmpty(asunto)){
					asunto = "<b>Expediente: </b>" + asunto;
				}
			}			
			//[eCenpri-Manu Ticket #303] - FIN - ALSIGM3 Dar informaci�n de los documentos anexados a los expedientes.

			// Obtener el responsable del expediente
			IProcess exp = invesFlowAPI.getProcess(numExp);			
			Responsible responsible = RespFactory.createResponsible(
					exp.getString("ID_RESP"));

			// Establecer el responsable
			mcontext.setUser(responsible);

			// Inicia el contexto de ejecuci�n para que se ejecuten
            // las reglas asociadas a la entidad //////////////////
            StateContext stateContext = mcontext.getStateContext();
            if (stateContext == null) {
                stateContext = new StateContext();
                mcontext.setStateContext(stateContext);
            }
            stateContext.setPcdId(exp.getInt("ID_PCD"));
            stateContext.setProcessId(exp.getKeyInt());
            stateContext.setNumexp(numExp);

			// Lista de referencias de los documentos creados en el gestor
			// documental para eliminarlos en caso de error
			List<String> documentRefs = new ArrayList<String>();

			// Ejecuci�n en un contexto transaccional
			boolean ongoingTX = mcontext.ongoingTX();
			boolean bCommit = false;

			try {
				// Abrir transacci�n para deshacer la creaci�n del expediente en caso de error
				if (!ongoingTX) {
					mcontext.beginTX();
				}

				Document doc;
				IItem docEntity;
				for (int i = 0; i < documents.size(); i++) {

					doc = (Document) documents.get(i);

					if (StringUtils.isNotBlank(doc.getId())) {

						int activeTaskId = getActiveTaskId(doc.getId());

						// Establecer el contexto
						stateContext.setTaskId(activeTaskId);
						stateContext.setStageId(getActiveStageId(activeTaskId));

						// Generar el documento asociado al tr�mite
						docEntity = createTaskDocument(activeTaskId, regNum, regDate, doc);

					} else {

						int activeStageId = getActiveStageId(numExp);

						// Establecer el contexto
						stateContext.setStageId(activeStageId);

						docEntity = createStageDocument(activeStageId,
								regNum, regDate, doc, true);
					}

					// Limpiar el contexto
					stateContext.setTaskId(0);
					stateContext.setStageId(0);

					// A�adir el GUID del documento a la lista
					documentRefs.add(docEntity.getString("INFOPAG"));
					//[eCenpri-Manu Ticket #303] - INICIO - ALSIGM3 Dar informaci�n de los documentos anexados a los expedientes.
					String tipo = doc.getCode();
					if(null != tipo && !tipo.equals("Solicitud Registro")){
						String nombreDoc = doc.getName();
						asunto = asunto + ",<br/><b>Documento Anexado: </b>" + nombreDoc + ",<br/> <b>Tipo de documento: </b>" + tipo;			//Se recorta si excede los caracteres, ya que da error y no puede insertar.
						//Se recorta si excede los caracteres, ya que da error y no puede insertar.
						if(null != expediente && asunto.length() > 252){
							asunto = asunto.substring(0, 250);
						}
						Notices.generateNotice(mcontext, exp.getInt("ID"), 0, 0, numExp, asunto, mcontext.getAPI().getProcess(exp.getInt("ID")).getString("ID_RESP"), Notices.TIPO_AVISO_DOCS_ANEXADOS_WS);
					}
					//[eCenpri-Manu Ticket #303] - FIN - ALSIGM3 Dar informaci�n de los documentos anexados a los expedientes.
				}

				//[eCenpri-Manu Ticket #303] - INICIO - ALSIGM3 Dar informaci�n de los documentos anexados a los expedientes.
//				// Generar un aviso en la bandeja de avisos electr�nicos
//				//generateNotice(entitiesAPI, exp.getInt("ID"), numExp, "notice.addDocuments", null);
//				Notices.generateNotice(mcontext, exp.getInt("ID"), 0, 0, numExp, "notice.addDocuments", mcontext.getAPI().getProcess(exp.getInt("ID")).getString("ID_RESP"), Notices.TIPO_AVISO_DOCS_ANEXADOS_WS);
				//[eCenpri-Manu Ticket #303] - FIN - ALSIGM3 Dar informaci�n de los documentos anexados a los expedientes.

				// Si todo ha sido correcto se hace commit de la transacci�n
				bCommit = true;

			} catch (Exception e) {

				LOGGER.error("Error al a�adir documentos al expediente", e);

				// Si se produce alg�n error
				// se eliminan los ficheros subidos al gestor documental
				deleteAttachedFiles(genDocAPI, documentRefs);

				throw new ISPACException(
						"Error al anexar documentos al expediente", e);
			}
			finally {
				if (!ongoingTX) {
					mcontext.endTX(bCommit);
				}
			}
    	}

		return true;
	}

    /**
     * A�ade documentos al tr�mite de un expediente.
     * @param numExp N�mero de expediente.
     * @param regNum N�mero de registro de entrada.
     * @param regDate Fecha de registro de entrada.
     * @param documents Lista de documentos asociados al expediente.
     * @return Cierto si los documentos se han creado correctamente.
     * @throws ISPACException Si se produce alg�n error.
     */
    public boolean addDocuments(String numExp, Intray intray) throws ISPACException {

		return addDocuments(numExp, intray, 0, 0);
	}

    /**
     * @param intray registro distribuid
     * @param numExp Numero de expediente
     * @param taskId
     * @throws ISPACException
     * Vincula al expediente el apunte de registro
     */
    private void linkRegister(Intray intray, String numExp, int taskId) throws ISPACException {
		//Comprobamos si la entidad SPAC_REGISTROS_ES esta vinculada al procedimiento para vincular el Apunte de registro al expediente a crear
		boolean associatedRegESEntity = RegisterHelper.isAssocitedRegistrosESEntity(mcontext, numExp);
		IItem itemRegisterES = null;


		if (associatedRegESEntity){

			String registerNumber = intray.getRegisterNumber();
			Date registerDate = intray.getRegisterDate();

			IEntitiesAPI entitiesAPI = mcontext.getAPI().getEntitiesAPI();
			IRegisterAPI registerAPI = mcontext.getAPI().getRegisterAPI();
			IThirdPartyAPI thirdPartyAPI = mcontext.getAPI().getThirdPartyAPI();

			//Si no esta asociado ya el apunte de registro con el expediente, se realiza la asociacion
			IItemCollection itemcol = entitiesAPI.queryEntities(SpacEntities.SPAC_REGISTROS_ES_NAME, "WHERE NREG = '" + registerNumber + "' AND NUMEXP = '"+DBUtil.replaceQuotes(numExp)+"' AND TP_REG = '" + RegisterType.ENTRADA + "'");
			if (!itemcol.next()){

				String asunto = intray.getMatter();
				if (StringUtils.isEmpty(asunto)){
					asunto = intray.getMatterTypeName();
				}

				itemRegisterES = entitiesAPI.createEntity(SpacEntities.SPAC_REGISTROS_ES_NAME, numExp);
				itemRegisterES.set("NREG", registerNumber);
				itemRegisterES.set("FREG", registerDate);
				itemRegisterES.set("TP_REG", RegisterType.ENTRADA);
				if (taskId != 0){
					itemRegisterES.set("ID_TRAMITE", taskId);
				}
				itemRegisterES.set("ASUNTO", asunto);
				itemRegisterES.set("ORIGINO_EXPEDIENTE", "NO");

            	// INTERESADO PRINCIPAL
            	if (!ArrayUtils.isEmpty(intray.getRegisterSender())) {

                	ThirdPerson thirdPerson = intray.getRegisterSender() [0];
                	if (thirdPerson != null) {

                		// Obtener el interesado principal
	                	IThirdPartyAdapter thirdParty = null;

	                	if ((thirdPartyAPI != null) && StringUtils.isNotBlank(thirdPerson.getId())) {
	                		thirdParty = thirdPartyAPI.lookupById(thirdPerson.getId(), thirdPerson.getAddressId(), null);
                		}

	                	if (thirdParty != null) {
	                		itemRegisterES.set("ID_INTERESADO", thirdParty.getIdExt());
	                		itemRegisterES.set("INTERESADO", thirdParty.getNombreCompleto());
	                	}else{
	                		itemRegisterES.set("INTERESADO", thirdPerson.getName());
	                	}

                	}
            	}
				itemRegisterES.store(mcontext);
				//Se vincula el expediente al apunte de registro
				registerAPI.linkExpedient(new RegisterInfo(null, registerNumber, null, RegisterType.ENTRADA), numExp);

				//Se da de alta los interesados como participantes en el expediente menos el primero que se asocia como interesado principal
				RegisterHelper.insertParticipants(mcontext, registerNumber,RegisterType.ENTRADA, numExp, true);

			}
		}
	}

	/**
     * Busqueda avanzada sobre expedientes, devuelve hasta un n�mero m�ximo de resultados
     * @param groupName Nombre de grupo
     * @param searchFormName Nombre del formulario de busqueda a utilizar
     * @param searchXML XML con los criterios de busqueda
     * @param domain dominio de busqueda (en funcion de la responsabilidad)
     * @return Resultado de la busqueda
     * @throws ISPACException
     */
    public SearchResultVO limitedSearch(String groupName, String searchFormName, String searchXML, int domain) throws ISPACException {
       	if (!SearchDomain.isDomain(domain)){
    		throw new ISPACException("Dominio desconocido");
    	}

       	String groupUID= getGroupUID(groupName);

		int expState = 0;
		IInvesflowAPI invesFlowAPI = mcontext.getAPI();
    	ISearchAPI searchAPI = invesFlowAPI.getSearchAPI();
		IItem item = searchAPI.getSearchForm(searchFormName);
		String configFile = item.getString("FRM_BSQ");

		Map<String, Object> values = new HashMap<String, Object>();
		Map<String, String> operators = new HashMap<String, String>();
		processSearchXML(searchXML, values, operators);
		SearchInfo searchinfo = searchAPI.getSearchInfo(configFile, domain, expState);
		searchinfo.setDescription(searchFormName);
		populateWithDataAndOperators(searchinfo, values, operators);

		// Ejecutar la b�squeda
		Responsible responsible = RespFactory.createResponsible(groupUID);
		mcontext.setUser(responsible);
    	SearchResultVO searchResultVO=new SearchResultVO();
		searchResultVO = searchAPI.getLimitedSearchResults(searchinfo);

		return searchResultVO;
    }
    /**
     * @deprecated
     * Busqueda avanzada sobre expedientes
     * @param groupName Nombre de grupo
     * @param searchFormName Nombre del formulario de busqueda a utilizar
     * @param searchXML XML con los criterios de busqueda
     * @param domain dominio de busqueda (en funcion de la responsabilidad)
     * @return Resultado de la busqueda
     * @throws ISPACException
     */
    public List<?> search(String groupName, String searchFormName, String searchXML, int domain) throws ISPACException {
    	if (!SearchDomain.isDomain(domain)){
    		throw new ISPACException("Dominio desconocido");
    	}

    	IInvesflowAPI invesFlowAPI = mcontext.getAPI();
    	ISearchAPI searchAPI = invesFlowAPI.getSearchAPI();

    	String groupUID=getGroupUID(groupName);

		int expState = 0;

		IItem item = searchAPI.getSearchForm(searchFormName);
		String configFile = item.getString("FRM_BSQ");

		Map<String, Object> values = new HashMap<String, Object>();
		Map<String, String> operators = new HashMap<String, String>();
		processSearchXML(searchXML, values, operators);
		SearchInfo searchinfo = searchAPI.getSearchInfo(configFile, domain, expState);
		searchinfo.setDescription(searchFormName);
		populateWithDataAndOperators(searchinfo, values, operators);

		// Ejecutar la b�squeda
		Responsible responsible = RespFactory.createResponsible(groupUID);
		mcontext.setUser(responsible);
		IItemCollection  results = searchAPI.getSearchResults(searchinfo);

		return CollectionBean.getBeanList(results);
    }

    /**
     * Establece datos de un registro nuevo o existente de una entidad para un expediente
     * @param entityName Nombre de entidad
     * @param numExp N�mero de expedientes
     * @param specificDataXML Datos espec�ficos de la entidad
     * @return identificador del registro insertado
     * @throws ISPACException
     */
    public int setRegEntity(String entityName, String numExp, String specificDataXML) throws ISPACException{
    	IEntitiesAPI entitiesAPI = mcontext.getAPI().getEntitiesAPI();
    	IProcedureAPI procedureAPI = mcontext.getAPI().getProcedureAPI();

    	IItem itemExp = entitiesAPI.getExpedient(numExp);
    	IItem procedure = procedureAPI.getProcedureById(itemExp.getInt("ID_PCD"));

    	List<?> list = processSpecificDataXML(entitiesAPI, specificDataXML, procedure, numExp);
    	if (list.isEmpty()){
    		return -1;
    	}
    	List<?> itemList = (List<?>)list.get(0);
    	if (itemList.isEmpty()){
    		return -1;
    	}
    	IItem itemInserted = (IItem)itemList.get(0);

		return itemInserted.getKeyInt();
    }


    /**
     * Obtiene los datos de un registro de una entidad para un expediente
     * @param entityName Nombre de entidad
     * @param numExp N�mero de expediente
     * @param regId Identificador del registro
     * @return Datos del registro
     * @throws ISPACException
     */
    public IItem getRegEntity(String entityName, String numExp, int regId) throws ISPACException{
    	IEntitiesAPI entitiesAPI = mcontext.getAPI().getEntitiesAPI();

    	IItem entityCT = entitiesAPI.getCatalogEntity(entityName);
    	int entityId = entityCT.getInt("ID");
    	if (regId != Constants.VALUE_DEFAULT_KEY){
    		return entitiesAPI.getEntity(entityId, regId);
    	}
    	IItemCollection itemcol = entitiesAPI.getEntities(entityId, numExp);
    	if (itemcol.next())
    		return itemcol.value();
    	return null;
    }

    /**
     * Obtiene todos los registros de una entidad para un expediente
     * @param entityName Nombre de entidad
     * @param numExp N�mero de expediente
     * @return Listado de registros
     * @throws ISPACException
     */
    public List<?> getAllRegsEntity(String entityName, String numExp) throws ISPACException{
    	IEntitiesAPI entitiesAPI = mcontext.getAPI().getEntitiesAPI();

    	IItem entityCT = entitiesAPI.getCatalogEntity(entityName);
    	int entityId = entityCT.getInt("ID");
    	IItemCollection itemcol = entitiesAPI.getEntities(entityId, numExp);

    	return CollectionBean.getBeanList(itemcol);
    }

	private void processSearchXML(String searchXML, Map<String, Object> values, Map<String, String> operators) {
		XMLFacade xml = new XMLFacade(searchXML);
		NodeIterator nodeItEntities = xml.getNodeIterator("search/entity");
		for (Node nodeEntity = nodeItEntities.nextNode(); nodeEntity != null; nodeEntity = nodeItEntities.nextNode()){
			String entityName = XMLFacade.getAttributeValue(nodeEntity, "name");
			NodeIterator nodeItFields = XMLFacade.getNodeIterator(nodeEntity, "field");
			for (Node nodeField = nodeItFields.nextNode(); nodeField != null; nodeField = nodeItFields.nextNode()){
				String fieldName = null;
				String fieldOperator = null;
				String fieldValue = null;
				NodeList nodeList = nodeField.getChildNodes();
				for (int i = 0; i < nodeList.getLength(); i++) {
					Node node = nodeList.item(i);
					if (StringUtils.equals(node.getNodeName(), "name")){
						fieldName = XMLFacade.getNodeValue(node);
					}else if (StringUtils.equals(node.getNodeName(), "operator")){
						fieldOperator = XMLFacade.getNodeValue(node);
					}else if (StringUtils.equals(node.getNodeName(), "value")){
						fieldValue = XMLFacade.getNodeValue(node);
					}
				}
				values.put(entityName+":"+fieldName, fieldValue);
				operators.put(entityName+":"+fieldName, fieldOperator);
			}
		}
	}

	private void populateWithDataAndOperators(SearchInfo searchinfo,
			Map<?, Object> values, Map<?, String> operators) throws ISPACException {
		Set<?>  valueKeys = values.keySet();
		Iterator<?> iter = valueKeys.iterator();
		while (iter.hasNext()) {
			String key = (String) iter.next();
			String[] keys = key.split(":");
			if (values.get(key) instanceof List) {
				List<?> value = (List<?>) values.get(key);
				searchinfo.setFieldValueForEntity(keys[0], keys[1], value);
			} else {
				String value = ((String) values.get(key)).replaceAll("'", "").trim();
				searchinfo.setFieldValueForEntity(keys[0], keys[1], value);
			}
			String operator = (String) operators.get(key);
			searchinfo.setFieldOperatorForEntity(keys[0], keys[1], operator);
		}
	}

    /**
     * Procesar los interesados,
     * guardando los no principales como intervinientes del expediente y
     * retornando el principal para establecerlo en la entidad del expediente.
     *
     * @param entitiesAPI API de entidades.
     * @param interested Lista de interesados.
     * @param numexp N�mero de expediente
     * @return Interesado principal si existe.
     * @throws ISPACException Si se produce alg�n error al procesar los interesados.
     */
    private InterestedPerson processInterested(IEntitiesAPI entitiesAPI,
    										   List<?> interested,
    										   String numexp) throws ISPACException {

    	InterestedPerson interestedPrincipal = null;

		if ((interested != null) &&
			(!interested.isEmpty())) {

			Iterator<?> it = interested.iterator();
			while (it.hasNext()) {

				InterestedPerson interestedPerson = (InterestedPerson) it.next();

				if ((interestedPerson.getIndPrincipal() != null ) &&
					(interestedPerson.getIndPrincipal().equals(InterestedPerson.IND_PRINCIPAL)) &&
					(interestedPrincipal == null)) {

					interestedPrincipal = interestedPerson;
				}
				else {

					// Generar un interviniente con los datos del interesado
					IItem interestedSecondary = entitiesAPI.createEntity(SpacEntities.SPAC_DT_INTERVINIENTES);

					interestedSecondary.set("ID_EXT", interestedPerson.getThirdPartyId());
					interestedSecondary.set("NDOC", interestedPerson.getNifcif());
					interestedSecondary.set("NUMEXP", numexp);
					interestedSecondary.set("NOMBRE", interestedPerson.getName());
					interestedSecondary.set("DIRNOT", interestedPerson.getPostalAddress());
					interestedSecondary.set("C_POSTAL", interestedPerson.getPostalCode());
					interestedSecondary.set("LOCALIDAD", interestedPerson.getPlaceCity());
					interestedSecondary.set("CAUT", interestedPerson.getRegionCountry());
					interestedSecondary.set("DIRECCIONTELEMATICA", interestedPerson.getTelematicAddress());
					interestedSecondary.set("TIPO_DIRECCION", interestedPerson.getNotificationAddressType());
					interestedSecondary.set("TFNO_FIJO", interestedPerson.getPhone());
					interestedSecondary.set("TFNO_MOVIL", interestedPerson.getMobilePhone());

					interestedSecondary.store(mcontext);
				}
			}
		}

		return interestedPrincipal;
    }

    /**
     * Procesar los datos espec�ficos del procedimiento del expediente.
     *
     * @param entitiesAPI API de entidades.
     * @param specificDataXML XML con los datos espec�ficos del expediente.
     * @param procedure Procedimiento del cat�logo del expediente iniciado.
     * @param numexp N�mero de expediente.
     * @return listado de los item creados, que se corresponderan con los registros de las entidades creados
     * @throws ISPACException Si se produce alg�n error al procesar los datos espec�ficos.
     */
    private List<List<?>> processSpecificDataXML(IEntitiesAPI entitiesAPI,
    									 String specificDataXML,
    									 IItem procedure,
    									 String numexp) throws ISPACException {
		if (LOGGER.isDebugEnabled()) {
			LOGGER.debug("specificDataXML: " + specificDataXML);
		}
		List<List<?>> list = new ArrayList<List<?>>();
		if (StringUtils.isNotBlank(specificDataXML)) {

    		String rtMappingXML = procedure.getString("CTPROCEDIMIENTOS:MAPEO_RT");
    		if (LOGGER.isDebugEnabled()) {
    			LOGGER.debug("MAPEO_RT: " + rtMappingXML);
    		}
    		if (StringUtils.isNotBlank(rtMappingXML)) {
		    	XMLFacade especificData = new XMLFacade(specificDataXML);
		    	Procedure procedureRT = new Procedure(rtMappingXML);

		    	// Procesar los mapeos de las tablas del procedimiento
		    	Iterator<?> it = procedureRT.getTableNames();
		    	while (it.hasNext()) {

		    		// Nombre de la tabla
		    		String name = (String) it.next();

		    		// Procesar la informaci�n de mapeo de la tabla
		    		Table table = procedureRT.getTable(name);

		    		// Crear los items en la entidad
		    		List<?> items = table.createItems(mcontext, numexp, especificData);
		    		if (!items.isEmpty()){
		    			list.add(items);
		    		}
		    	}
    		}
    	}
		return list;
    }

    /**
     * Procesar los documentos del expediente.
     *
     * @param entitiesAPI API de entidades.
     * @param genDocAPI API de generaci�n de documentos.
     * @param commonData Datos comunes para todos los expedientes.
     * @param documents Lista de documentos asociados al expediente.
     * @param numexp N�mero de expediente.
     * @param documentRefs Lista de referencias a los documentos creados en el gestor documental.
     * @throws ISPACException Si se produce alg�n error al procesar los documentos.
     */
    private void processDocuments(IEntitiesAPI entitiesAPI,
    							  IGenDocAPI genDocAPI,
    							  CommonData commonData,
    							  List<?> documents,
    							  String numexp,
    							  List<String> documentRefs) throws ISPACException {

		if ((documents != null) &&
			(!documents.isEmpty())) {

			// Obtener la fase activa
			int stageId = getActiveStageId(numexp);

			Iterator<?> it = documents.iterator();
			while (it.hasNext()) {

				Document document = (Document) it.next();

				try {
					// Generar un documento asociado a la fase activa del expediente
					IItem docEntity = createStageDocument(stageId,
							commonData.getRegisterNumber(),
							commonData.getRegisterDate(), document, true);

					documentRefs.add(docEntity.getString("INFOPAG"));
				}
				catch (Exception e) {
					LOGGER.error("Error al generar el documento", e);
					throw new ISPACException("No se pudo generar el documento: " + document.getName());
				}
			}
		}
    }

    /**
     * Generar un aviso de expediente iniciado.
     *
     * @param entitiesAPI API de entidades.
     * @param processId Identificador del proceso del expediente.
     * @param numexp N�mero de expediente.
     * @param message Mensaje del aviso.
     * @param initSystem C�digo del sistema desde el que se genera el aviso.
     * @throws ISPACException Si se produce alg�n error al generar el aviso.
     */
//    private void generateNotice(IEntitiesAPI entitiesAPI,
//    							int processId,
//    							String numexp,
//    							String message,
//    							String initSystem) throws ISPACException {
//
//    	IProcess process = mcontext.getAPI().getProcess(processId);
//    	IItem notice = entitiesAPI.createEntity(SpacEntities.SPAC_AVISOS_ELECTRONICOS);
//
//    	notice.set("ID_PROC", processId);
//    	notice.set("TIPO_DESTINATARIO", 1);
//    	notice.set("FECHA", new Date());
//    	notice.set("ID_EXPEDIENTE", numexp);
//    	notice.set("ESTADO_AVISO", 0);
//    	notice.set("MENSAJE", message + ","+initSystem);
//
//    	if (initSystem == null || StringUtils.equalsIgnoreCase(initSystem, "RT")) {
//    		notice.set("TIPO_AVISO", 2);
//    	} else if (StringUtils.equalsIgnoreCase(initSystem, "SIGEM")) {
//    		notice.set("TIPO_AVISO", 1);
//    	} else {
//    		notice.set("TIPO_AVISO", 3);
//    	}
//
//    	// Generar el aviso al responsable del proceso
//    	//notice.set("UID_DESTINATARIO", mcontext.getUser().getUID());
//    	notice.set("UID_DESTINATARIO", process.get("ID_RESP"));
//
//    	notice.store(mcontext);
//    }


//    private void generateNotice(ClientContext cct,
//			int processId,
//			String numexp,
//			String message,
//			String initSystem) throws ISPACException {
//
//    	Notices notices = new Notices(cct);
//    	IProcess process = mcontext.getAPI().getProcess(processId);
//    	int tipoAviso;
//    	if (initSystem == null || StringUtils.equalsIgnoreCase(initSystem, "RT")) {
//    		tipoAviso=2;
//    	} else if (StringUtils.equalsIgnoreCase(initSystem, "SIGEM")) {
//    		tipoAviso = 1;
//    	} else {
//    		tipoAviso = 3;
//    	}
//    	notices.generateNotice(cct, processId, 0, 0, numexp, message, process.getString("ID_RESP"), tipoAviso);
//}


    /**
     * Eliminar los documentos f�sicos del gestor documental.
     *
     * @param genDocAPI API de generac�n de documentos
     * @param documentRefs Lista de referencias a los documentos creados en el gestor documental.
     */
    private void deleteAttachedFiles(IGenDocAPI genDocAPI,
    						  		 List<String> documentRefs) {

    	if (!documentRefs.isEmpty()) {

    		Iterator<String> it = documentRefs.iterator();
    		while (it.hasNext()) {

    			String docref = (String) it.next();

		    	if ((docref != null) &&
			    	(!docref.equals(""))) {

		    		Object connectorSession = null;
		    		try {
		    			connectorSession = genDocAPI.createConnectorSession();
		    			// Eliminar el documento fisico del gestor documental
			            genDocAPI.deleteDocument(connectorSession, docref);
			    	}
		    		catch (Exception e) {}
		    		finally {
						if (connectorSession != null) {
							try{
								genDocAPI.closeConnectorSession(connectorSession);
							}catch(Exception e){}
						}
			    	}
		    	}
    		}
    	}
    }

    /**
     * Obtener un procedimiento del cat�logo a partir de su c�digo.
     *
     * @param entitiesAPI API de entidades.
     * @param code C�digo del procedimiento.
     * @return Procedimiento del cat�logo.
     * @throws ISPACException Si se produce alg�n error al obtener el procedimiento.
     */
    /*
    private IItem getCatalogProcedure(IEntitiesAPI entitiesAPI,
    						   		  String code) throws ISPACException {

		// TODO Validar el tipo de documento
		String strSQL = "WHERE COD_PCD = '" + DBUtil.replaceQuotes(code) + "'";
		IItemCollection collection = entitiesAPI.queryEntities(SpacEntities.SPAC_CT_PROCEDIMIENTOS, strSQL);

		if (!collection.next()) {
			throw new ISPACException("C�digo de procedimiento desconocido: " + code);
		}

		// Procedimiento
		return collection.value();
    }
    */

    /**
     * Obtiene el tipo de documento a partir del c�digo.
     * @param code C�digo del tipo de documento.
     * @return Tipo de documento.
     * @throws ISPACException si ocurre alg�n error.
     */
    private IItem getDocType(String code) throws ISPACException {

		IEntitiesAPI entitiesAPI = mcontext.getAPI().getEntitiesAPI();

		// Buscar tipo de documento por c�digo
		String sql = new StringBuffer("WHERE COD_TPDOC='").append(DBUtil.replaceQuotes(code)).append("'").toString();
		IItemCollection docTypes = entitiesAPI.queryEntities(SpacEntities.SPAC_CT_TPDOC, sql);
		if (!docTypes.next()) {

			// Buscar tipo de documento por nombre
			sql = new StringBuffer("WHERE NOMBRE='").append(DBUtil.replaceQuotes(code)).append("'").toString();
			docTypes = entitiesAPI.queryEntities(SpacEntities.SPAC_CT_TPDOC, sql);
			if (!docTypes.next()) {
				throw new ISPACException("C�digo/nombre de documento desconocido: " + code);
			}
		}

		return docTypes.value();
    }

    private int getActiveTaskId(String docId) throws ISPACException {
    	IEntitiesAPI entitiesAPI = mcontext.getAPI().getEntitiesAPI();

    	int taskInfoId = -1;
    	int ix = docId.lastIndexOf("-");
    	if (ix > 0) {
    		taskInfoId = TypeConverter.parseInt(docId.substring(0, ix), -1);
    	} else {
    		taskInfoId = TypeConverter.parseInt(docId, -1);
    	}

    	if (taskInfoId == -1) {
			throw new ISPACException("El documento no contiene un id correcto: "
    				+ docId);
    	}

    	// Informaci�n del tr�mite
		IItem taskInfo = entitiesAPI.getEntity(SpacEntities.SPAC_INFOTRAMITE,
				taskInfoId);

    	return taskInfo.getInt("ID_TRAMITE");
    }

    private int getActiveStageId(String numExp) throws ISPACException {

		IEntitiesAPI entitiesAPI = mcontext.getAPI().getEntitiesAPI();

		String sql = new StringBuffer("WHERE NUMEXP = '").append(DBUtil.replaceQuotes(numExp)).append("'").toString();

		IItemCollection collection = entitiesAPI.queryEntities(SpacEntities.SPAC_FASES, sql);
		IItem item = collection.value();
		return item.getKeyInt();
    }

    private int getActiveStageId(int activeTaskId) throws ISPACException {

		ITask activeTask = mcontext.getAPI().getTask(activeTaskId);
		return activeTask.getInt("ID_FASE_EXP");
    }

    private IItem createTaskDocument(int taskId, String regNum, Date regDate,
    		Document doc) throws ISPACException {

		return createTaskDocument(taskId, 0, regNum, regDate, doc);
    }

    protected String getIntrayOrigen(Intray intray) throws ISPACException {

		String origen = intray.getRegisterOrigin();
		if (StringUtils.isBlank(origen)) {
			// Remitentes del registro
			ThirdPerson[] remitentes = intray.getRegisterSender();
			if ((remitentes != null) && (remitentes.length > 0)) {
				ThirdPerson remitente = remitentes [0];
				origen = remitente.getFiscalId();
				if (StringUtils.isBlank(origen)) {
					origen = remitente.getName();
				} else {
					origen += " " + remitente.getName();
				}
			} else {
				origen = DOC_ORIGIN_PRESENTIAL;
			}
		}

		return origen;
    }

    private IItem createTaskDocument(int taskId, int typeDocId, Intray intray, Document doc) throws ISPACException {

		IGenDocAPI genDocAPI = mcontext.getAPI().getGenDocAPI();

		// Obtener el tipo MIME
    	String mimeType = MimetypeMapping.getMimeType(doc.getExtension());

		if (typeDocId == 0) {

			// Validar el tipo de documento
			IItem docType = getDocType(doc.getCode());
			typeDocId = docType.getInt("ID");
		}

    	// Generar un documento asociado al tr�mite
		IItem docEntity = genDocAPI.createTaskDocument(taskId, typeDocId);

		// Establecer los datos del documento
		docEntity.set("ORIGEN", getIntrayOrigen(intray));
		docEntity.set("DESTINO", intray.getRegisterDestination());
		docEntity.set("TP_REG", DOC_REG_TYPE);
		docEntity.set("NREG", intray.getRegisterNumber());
		docEntity.set("FREG", intray.getRegisterDate());
		docEntity.set("EXTENSION", doc.getExtension());
		docEntity.store(mcontext);

		Object connectorSession = null;
		try {
			connectorSession = genDocAPI.createConnectorSession();
			// Subir el fichero al gestor documental
			docEntity = genDocAPI.attachTaskInputStream(connectorSession, taskId,
					docEntity.getKeyInt(), doc.getContent(), doc.getLength(),
					mimeType, doc.getName());
		}finally {
			if (connectorSession != null) {
				genDocAPI.closeConnectorSession(connectorSession);
			}
		}

		return docEntity;
    }

    private IItem createTaskDocument(int taskId, int typeDocId, String regNum, Date regDate,
			Document doc) throws ISPACException {

		IGenDocAPI genDocAPI = mcontext.getAPI().getGenDocAPI();

		// Obtener el tipo MIME
		String mimeType = MimetypeMapping.getMimeType(doc.getExtension());

		if (typeDocId == 0) {

			// Validar el tipo de documento
			IItem docType = getDocType(doc.getCode());
			typeDocId = docType.getInt("ID");
		}

		// Generar un documento asociado al tr�mite
		IItem docEntity = genDocAPI.createTaskDocument(taskId, typeDocId);

		// Establecer los datos del documento
		docEntity.set("ORIGEN", DOC_ORIGIN_TELEMATIC);
		docEntity.set("TP_REG", DOC_REG_TYPE);
		docEntity.set("NREG", regNum);
		docEntity.set("FREG", regDate);
		docEntity.set("EXTENSION", doc.getExtension());
		docEntity.store(mcontext);

		Object connectorSession = null;
		try {
			connectorSession = genDocAPI.createConnectorSession();
			// Subir el fichero al gestor documental
			docEntity = genDocAPI.attachTaskInputStream(connectorSession, taskId,
					docEntity.getKeyInt(), doc.getContent(), doc.getLength(),
					mimeType, doc.getName());
    	}finally {
			if (connectorSession != null) {
				genDocAPI.closeConnectorSession(connectorSession);
			}
    	}

		return docEntity;
    }

    private IItem createStageDocument(int stageId, String regNum, Date regDate,
			Document doc, boolean telematic) throws ISPACException {

		return createStageDocument(stageId, 0, regNum, regDate, doc, telematic);
    }

    private IItem createStageDocument(int stageId, int typeDocId, String regNum, Date regDate,
    		Document doc, boolean telematic) throws ISPACException {

		IGenDocAPI genDocAPI = mcontext.getAPI().getGenDocAPI();

		// Obtener el tipo MIME
    	String mimeType = MimetypeMapping.getMimeType(doc.getExtension());

		if (typeDocId == 0) {

			// Validar el tipo de documento
			IItem docType = getDocType(doc.getCode());
			typeDocId = docType.getInt("ID");
		}

    	// Generar un documento asociado la fase
		IItem docEntity = genDocAPI.createStageDocument(stageId, typeDocId);

		// Establecer los datos del documento
		docEntity.set("ORIGEN", telematic ? DOC_ORIGIN_TELEMATIC : DOC_ORIGIN_PRESENTIAL);
		docEntity.set("TP_REG", DOC_REG_TYPE);
		docEntity.set("EXTENSION", doc.getExtension());
		docEntity.set("NREG", regNum);
		docEntity.set("FREG", regDate);
		docEntity.store(mcontext);

		Object connectorSession = null;
		try {
			connectorSession = genDocAPI.createConnectorSession();

			// Subir el fichero al gestor documental
			docEntity = genDocAPI.attachStageInputStream(connectorSession, stageId,
					docEntity.getKeyInt(), doc.getContent(), doc.getLength(),
					mimeType, doc.getName());
		} finally {
			if (connectorSession != null) {
				genDocAPI.closeConnectorSession(connectorSession);
			}
		}

		return docEntity;
    }

    private IItem createStageDocument(int stageId, int typeDocId, Intray intray,
			Document doc) throws ISPACException {

		IGenDocAPI genDocAPI = mcontext.getAPI().getGenDocAPI();

		// Obtener el tipo MIME
		String mimeType = MimetypeMapping.getMimeType(doc.getExtension());

		if (typeDocId == 0) {

			// Validar el tipo de documento
			IItem docType = getDocType(doc.getCode());
			typeDocId = docType.getInt("ID");
		}

		// Generar un documento asociado la fase
		IItem docEntity = genDocAPI.createStageDocument(stageId, typeDocId);

		// Establecer los datos del documento
		docEntity.set("ORIGEN", getIntrayOrigen(intray));
		docEntity.set("DESTINO", intray.getRegisterDestination());
		docEntity.set("TP_REG", DOC_REG_TYPE);
		docEntity.set("EXTENSION", doc.getExtension());
		docEntity.set("NREG", intray.getRegisterNumber());
		docEntity.set("FREG", intray.getRegisterDate());
		docEntity.store(mcontext);

		Object connectorSession = null;
		try {
			connectorSession = genDocAPI.createConnectorSession();

			// Subir el fichero al gestor documental
			docEntity = genDocAPI.attachStageInputStream(connectorSession, stageId,
					docEntity.getKeyInt(), doc.getContent(), doc.getLength(),
					mimeType, doc.getName());
		} finally {
			if (connectorSession != null) {
				genDocAPI.closeConnectorSession(connectorSession);
			}
		}

		return docEntity;
    }

    private String getGroupUID(String groupName) throws ISPACException{
    	IInvesflowAPI invesFlowAPI = mcontext.getAPI();

    	String groupUID = null;
    	IItemCollection itemcol = invesFlowAPI.getRespManagerAPI().getAllGroups();
    	while(itemcol.next()){
    		IResponsible group = (IResponsible)itemcol.value();
    		if (StringUtils.equals(group.getName(), groupName)){
    			groupUID = group.getUID();
    		}
    	}
    	if (groupUID == null){
    		throw new ISPACException("Nombre de grupo desconocido");
    	}
    	return groupUID;
    }

    public boolean addDocuments(String numExp, Intray intray, int taskId) throws ISPACException {

		return addDocuments(numExp, intray, taskId, 0);
    }

	public boolean addDocuments(String numExp, Intray intray, int taskId, int typeDocId) throws ISPACException {

    	if (StringUtils.isNotBlank(numExp) && (intray != null)) {

			IInvesflowAPI invesFlowAPI = mcontext.getAPI();
			IInboxAPI inboxAPI = invesFlowAPI.getInboxAPI();

			// Obtener la informaci�n del expediente
			IProcess exp = invesFlowAPI.getProcess(numExp);

			// Inicia el contexto de ejecuci�n para que se ejecuten
            // las reglas asociadas a la entidad //////////////////
            StateContext stateContext = mcontext.getStateContext();
            if (stateContext == null) {
                stateContext = new StateContext();
                mcontext.setStateContext(stateContext);
            }
            stateContext.setPcdId(exp.getInt("ID_PCD"));
            stateContext.setProcessId(exp.getKeyInt());
            stateContext.setNumexp(numExp);

			// Identificadores de los documentos
			Annexe[] annexes = inboxAPI.getAnnexes(intray.getId());
			if (annexes != null && annexes.length > 0) {
				IGenDocAPI genDocAPI = invesFlowAPI.getGenDocAPI();

				// Identificador de la fase activa del expediente
				int activaStageId = getActiveStageId(numExp);

				// Lista de referencias de los documentos creados en el gestor
				// documental para eliminarlos en caso de error
				List<String> documentRefs = new ArrayList<String>();

				try {

					// Gestor de ficheros temporales
					FileTemporaryManager fileTempMgr = FileTemporaryManager.getInstance();

					for (int i = 0; i < annexes.length; i++) {

						// Informaci�n del anexo
						Annexe annex = annexes[i];

						File file = null;

						try {

							// Crear fichero temporal
							file = fileTempMgr.newFile();

							// Obtener fichero del anexo
							FileOutputStream out = new FileOutputStream(file);
							inboxAPI.getAnnexe(intray.getId(), annex.getId(), out);
							out.close();

							// Componer informaci�n del documento
							Document doc = new Document();
							
							if(StringUtils.isNotEmpty(annex.getTipoDoc())){
								doc.setCode(annex.getTipoDoc());
							} else {
								doc.setCode(ISPACConfiguration.getInstance().get(ISPACConfiguration.SICRES_INTRAY_DEFAULT_DOCUMENT_TYPE));
							}
							
							doc.setName(annex.getName());
							doc.setExtension(annex.getExt());
							doc.setLength(new Long(file.length()).intValue());
							FileInputStream in = new FileInputStream(file);
							doc.setContent(in);

							IItem docEntity = null;

							if (taskId == 0) {
								// Crear documento en la fase activa
								docEntity = createStageDocument(activaStageId, typeDocId, intray, doc);
							} else{
								// Crear documento en el tr�mite abierto
								docEntity = createTaskDocument(taskId, typeDocId, intray, doc);
							}

							in.close();

							// A�adir el GUID del documento a la lista
							documentRefs.add(docEntity.getString("INFOPAG"));

						} finally {

							// Eliminar el fichero temporal
							if (file != null) {
								fileTempMgr.delete(file);
							}
						}
					}

				} catch (Exception e) {

					LOGGER.error("Error al a�adir documentos al expediente", e);

					// Eliminar los ficheros subidos al gestor documental
					deleteAttachedFiles(genDocAPI, documentRefs);

					throw new ISPACException("Error al anexar documentos al expediente", e);
				}
    		}
			linkRegister(intray, numExp, taskId);
    	}
		return true;
	}
	
	 /**
     * A�ade documentos al tr�mite de un expediente.
     * @param numExp N�mero de expediente.
     * @param idTramite Identificador del tr�mite al que asociar el documento.
     * @param regNum N�mero de registro de entrada.
     * @param regDate Fecha de registro de entrada.
     * @param documents Lista de documentos asociados al expediente.
     * @return Cierto si los documentos se han creado correctamente.
     * @throws ISPACException Si se produce alg�n error.
     */
	 public boolean addDocuments(String numExp, int idTramite, String regNum, Date regDate, List<?> documents) throws ISPACException {
		 
		 boolean bCommit = false;
		 
		 if (!CollectionUtils.isEmpty(documents)) {

			IInvesflowAPI invesFlowAPI = mcontext.getAPI();
			IGenDocAPI genDocAPI = invesFlowAPI.getGenDocAPI();
			
			String asunto = ExpedientesUtil.getAsunto(mcontext, numExp);
			if(StringUtils.isNotEmpty(asunto)){
				asunto = "<b>Expediente: </b>" + asunto;
			}
			
			// Obtener el responsable del expediente
			IProcess exp = invesFlowAPI.getProcess(numExp);			
			Responsible responsible = RespFactory.createResponsible( exp.getString("ID_RESP"));

			// Establecer el responsable
			mcontext.setUser(responsible);

			// Inicia el contexto de ejecuci�n para que se ejecuten
            // las reglas asociadas a la entidad //////////////////
            StateContext stateContext = mcontext.getStateContext();
            if (stateContext == null) {
                stateContext = new StateContext();
                mcontext.setStateContext(stateContext);
            }
            stateContext.setPcdId(exp.getInt("ID_PCD"));
            stateContext.setProcessId(exp.getKeyInt());
            stateContext.setNumexp(numExp);

			// Lista de referencias de los documentos creados en el gestor
			// documental para eliminarlos en caso de error
			List<String> documentRefs = new ArrayList<String>();

			// Ejecuci�n en un contexto transaccional
			boolean ongoingTX = mcontext.ongoingTX();
			

			try {
				// Abrir transacci�n para deshacer la creaci�n del expediente en caso de error
				if (!ongoingTX) {
					mcontext.beginTX();
				}

				Document doc;
				IItem docEntity;
				for (int i = 0; i < documents.size(); i++) {

					doc = (Document) documents.get(i);

					if (-1 < idTramite) {
						stateContext.setTaskId(idTramite);
						stateContext.setStageId(getActiveStageId(idTramite));

						// Generar el documento asociado al tr�mite
						docEntity = createTaskDocument(idTramite, regNum, regDate, doc);


						// Limpiar el contexto
						stateContext.setTaskId(0);
						stateContext.setStageId(0);
	
						// A�adir el GUID del documento a la lista
						documentRefs.add(docEntity.getString("INFOPAG"));
						String tipo = doc.getCode();
						
						if(null != tipo && !tipo.equals("Solicitud Registro")){
							String nombreDoc = doc.getName();
							asunto = asunto + ",<br/><b>Documento Anexado: </b>" + nombreDoc + ",<br/> <b>Tipo de documento: </b>" + tipo;			//Se recorta si excede los caracteres, ya que da error y no puede insertar.
							//Se recorta si excede los caracteres, ya que da error y no puede insertar.
							if(asunto.length() > 252){
								asunto = asunto.substring(0, 250);
							}
							Notices.generateNotice(mcontext, exp.getInt("ID"), 0, 0, numExp, asunto, mcontext.getAPI().getProcess(exp.getInt("ID")).getString("ID_RESP"), Notices.TIPO_AVISO_DOCS_ANEXADOS_WS);
						}//[eCenpri-Manu Ticket #303] - FIN - ALSIGM3 Dar informaci�n de los documentos anexados a los expedientes.
					}
				}
					
				//Si todo ha sido correcto se hace commit de la transacci�n
				bCommit = true;

			} catch (Exception e) {
				LOGGER.error("Error al a�adir documentos al tramite: " + idTramite + " en el expediente: " + numExp + ". " + e.getMessage(), e);
				deleteAttachedFiles(genDocAPI, documentRefs);

				throw new ISPACException("Error al a�adir documentos al tramite: " + idTramite + " en el expediente: " + numExp + ". " + e.getMessage(), e);
				
			} finally {
				if (!ongoingTX) {
					mcontext.endTX(bCommit);
				}
			}
    	}

		return bCommit;
	}
}