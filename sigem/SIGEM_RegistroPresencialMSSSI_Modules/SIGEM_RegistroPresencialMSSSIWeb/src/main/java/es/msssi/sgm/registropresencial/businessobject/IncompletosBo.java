/*
* Copyright 2016 Ministerio de Sanidad, Servicios Sociales e Igualdad 
* Licencia con arreglo a la EUPL, Versi�n 1.1 o -en cuanto sean aprobadas por laComisi�n Europea- versiones posteriores de la EUPL (la �Licencia�); 
* Solo podr� usarse esta obra si se respeta la Licencia. 
* Puede obtenerse una copia de la Licencia en: 
* http://joinup.ec.europa.eu/software/page/eupl/licence-eupl 
* Salvo cuando lo exija la legislaci�n aplicable o se acuerde por escrito, el programa distribuido con arreglo a la Licencia se distribuye �TAL CUAL�, SIN GARANT�AS NI CONDICIONES DE NING�N TIPO, ni expresas ni impl�citas. 
* V�ase la Licencia en el idioma concreto que rige los permisos y limitaciones que establece la Licencia. 
*/

package es.msssi.sgm.registropresencial.businessobject;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.ieci.tecdoc.common.exception.BookException;
import com.ieci.tecdoc.common.exception.DistributionException;
import com.ieci.tecdoc.common.exception.SessionException;
import com.ieci.tecdoc.common.exception.ValidationException;
import com.ieci.tecdoc.common.utils.ScrRegStateByLanguage;
import com.ieci.tecdoc.isicres.session.distribution.DistributionSessionUtil;
import com.ieci.tecdoc.isicres.session.folder.FolderSession;
import com.ieci.tecdoc.isicres.usecase.UseCaseConf;

import es.msssi.sgm.registropresencial.beans.ListBooksBean;
import es.msssi.sgm.registropresencial.errors.RPBookException;
import es.msssi.sgm.registropresencial.errors.RPGenericException;
import es.msssi.sgm.registropresencial.utils.KeysRP;
import es.msssi.sgm.registropresencial.utils.ResourceRP;

/**
 * Clase q implementa IGenericBo que contiene los m�todos relacionados con la
 * distribuci�n.
 * 
 * @author cmorenog
 * 
 */
public class IncompletosBo extends DistributionSessionUtil implements IGenericBo {

    private static final Logger LOG = Logger.getLogger(IncompletosBo.class.getName());
  

    /**
     * Devuelve la lista de alarmas de la bandeja de entrada de distribuci�n.
     * 
     * @param useCaseConf
     *            configuraci�n de la aplicaci�n.
     * @return lista de alarmas de distribuci�n
     * @throws DistributionException
     *             error la distribuci�n
     * @throws SessionException
     *             sesi�n nula
     * @throws ValidationException
     *             error validaci�n
     * */
    public List<String> getListMessageInit( UseCaseConf useCaseConf) throws DistributionException, SessionException, ValidationException { 
    	
    	ArrayList<String> result = new ArrayList<String>();
    	int numeroIncompletos = 0;
    	
    	BooksBo booksBo = new BooksBo();
    	
    	try {
			ListBooksBean listBook = booksBo.getBooks(useCaseConf);
			
			for (ScrRegStateByLanguage libro : listBook.getInList()){
				Integer idBook = libro.getIdocarchhdrId();
				
				numeroIncompletos = FolderSession.getIncompletRegisterSize( useCaseConf.getSessionID(), idBook, useCaseConf.getEntidadId());
				
		    	if (numeroIncompletos > 0) {
		    		result.add(MessageFormat.format( ResourceRP.getInstance( useCaseConf.getLocale()).getProperty( KeysRP.I18N_INCOMPLETOS_FOR_USER), new Object[] { libro.getIdocarchhdrName() }));
		    	}
			}
		
   		} catch (RPGenericException e) {
			LOG.error("ERROR al recuperar la lista de libros para comprobar si hay distribuciones pendiente en ORVE. " + e.getMessage(), e);
		
		} catch (RPBookException e) {
			LOG.error("ERROR al recuperar la lista de libros para comprobar si hay distribuciones pendiente en ORVE. " + e.getMessage(), e);
		
		} catch (BookException e) {
			LOG.error("ERROR al recuperar el libro. " + e.getMessage(), e);
		}
    	
    	return result;
    }
}
