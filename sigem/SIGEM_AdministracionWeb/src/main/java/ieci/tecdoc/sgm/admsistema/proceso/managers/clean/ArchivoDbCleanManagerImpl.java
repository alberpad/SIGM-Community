package ieci.tecdoc.sgm.admsistema.proceso.managers.clean;

import ieci.tecdoc.sgm.admsistema.util.Defs;

import java.util.HashMap;
import java.util.Map;

/** 
 * @author Iecisa 
 * @version $Revision$ 
 *
 */
public class ArchivoDbCleanManagerImpl extends DefaultDbCleanManagerImpl {

	private static final String DB_NAME = "archivo";
	
	private static final Map SQL_SENTENCES_MAP = new HashMap();
	static {
		SQL_SENTENCES_MAP.put("*", new String[] {
				
				"DELETE FROM ASGTSNSEC",
				"DELETE FROM ASGTSNSECUI",
				"DELETE FROM ASGTSNSECUDOC",
				"DELETE FROM ASGTUDOCSDF",
				"DELETE FROM ASGTUINSTALACIONREEA",
				"DELETE FROM ASGTUDOCENUI",
				"DELETE FROM ASGTUNIDADDOCRE",
				"DELETE FROM ASGTUINSTALACIONRE",
				"DELETE FROM ASGTRENTREGA",
				"DELETE FROM ASGTDETALLEPREV",
				"DELETE FROM ASGTPREVISION",
				"DELETE FROM ASGDUDOCENUI",
				"DELETE FROM ASGDUINSTALACION",
				"DELETE FROM ASGFUNIDADDOC",
				"DELETE FROM ASGPSNSEC",
				"DELETE FROM ASGPPRORROGA",
				"DELETE FROM ASGPPRESTAMO",
				"DELETE FROM ASGPCONSULTA",
				"DELETE FROM ASGPSOLICITUDUDOC",
				"DELETE FROM ASGFVOLUMENSERIE",
				"DELETE FROM ASGFNUMSECSEL",
				"DELETE FROM ASGFHISTUDOC",
				"DELETE FROM ASGFELIMSELUDOC",
				"DELETE FROM ASGFELIMSERIE",
				"DELETE FROM ASGFUDOCSDF",
				"DELETE FROM ADPCUSODOCVIT",
				"DELETE FROM ADPCTIPODOCVIT",
				"DELETE FROM ADPCTIPODOCPROC",
				"DELETE FROM ADPCDOCUMENTOVIT",
		    	"DELETE FROM AGOBJBLOQUEO WHERE TIPOOBJ = 4",
		    	"DELETE FROM ADVCTEXTCF WHERE IDELEMENTOCF IN (SELECT ID FROM ASGFELEMENTOCF WHERE TIPO = 6)",
		    	"DELETE FROM ADVCTEXTLCF WHERE IDELEMENTOCF IN (SELECT ID FROM ASGFELEMENTOCF WHERE TIPO = 6)",
		    	"DELETE FROM ADVCNUMCF WHERE IDELEMENTOCF IN (SELECT ID FROM ASGFELEMENTOCF WHERE TIPO = 6)",
		    	"DELETE FROM ADVCFECHACF WHERE IDELEMENTOCF IN (SELECT ID FROM ASGFELEMENTOCF WHERE TIPO = 6)",
		    	"DELETE FROM ADVCREFCF WHERE IDELEMENTOCF IN (SELECT ID FROM ASGFELEMENTOCF WHERE TIPO = 6)",
		    	"DELETE FROM ADOCCLASIFCF WHERE IDELEMENTOCF IN (SELECT ID FROM ASGFELEMENTOCF WHERE TIPO = 6)",
		    	"DELETE FROM ADOCDOCUMENTOCF WHERE IDELEMENTOCF IN (SELECT ID FROM ASGFELEMENTOCF WHERE TIPO = 6)",
		    	"DELETE FROM ASGFELEMENTOCF WHERE TIPO = 6",
		    	"DELETE FROM ADOCTCAPTURA WHERE TIPOOBJ = 12",
		    	"DELETE FROM AADATOSTRAZA WHERE IDTRAZA IN (SELECT ID FROM AATRAZA WHERE MODULO IN (2, 3) OR ACCION IN (100, 101, 102, 103, 104, 105, 106, 107, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 400, 402, 403, 404, 405, 406, 407, 417, 418, 419, 500, 501, 502, 503, 510, 511, 618, 634, 642, 643, 644, 645, 646, 647, 648, 649, 650, 651, 652, 653, 655, 656, 657, 1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008))",
		    	"DELETE FROM AATRAZA WHERE MODULO IN (2, 3) OR ACCION IN (100, 101, 102, 103, 104, 105, 106, 107, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119,120, 121, 400, 402, 403, 404, 405, 406, 407, 417, 418, 419, 500, 501, 502, 503, 510, 511, 618, 634, 642, 643, 644, 645, 646, 647, 648, 649, 650, 651, 652, 653, 655, 656, 657, 1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008)",
		    	"UPDATE ASGDHUECO SET ESTADO='L', IDUINSTALACION=NULL, IDRELENTREGA=NULL, ORDENENRELACION=0"
		});
	}

	private static final String[][] DYNAMIC_TABLES_FOR_CLEANING = new String[0][0];
	
	private static final String[] DYNAMIC_TABLES_FOR_CLEANING_CONDITION = new String [0];
	
	
	/**
	 * Constructor.
	 */
	public ArchivoDbCleanManagerImpl() {
		super();
	}

	public String getDbName() {
		return DB_NAME;
	}

	public Map getSQLSentences() {
		return SQL_SENTENCES_MAP;
	}

	public String[][] getDynamicTablesForCleaning() {
		return DYNAMIC_TABLES_FOR_CLEANING;
	}

	public String[] getDynamicTablesForCleaningCondition() {
		return DYNAMIC_TABLES_FOR_CLEANING_CONDITION;
	}

	public String getCleanUserKey() {
		return Defs.BD_USUARIO_AD_IMP;
	}

	public String getCleanUserPwdKey() {
		return Defs.BD_PASS_AD_IMP;
	}
}
