package com.citytechinc.aem.groovy.console.servlets

import org.apache.commons.lang3.StringUtils
import org.apache.felix.scr.annotations.Activate
import org.apache.felix.scr.annotations.sling.SlingServlet
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.adapter.AdapterFactory
import org.apache.sling.api.resource.ResourceResolver
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants
import org.osgi.service.component.ComponentConstants

import javax.servlet.ServletException

@SlingServlet(paths = "/bin/groovyconsole/services")
class ServicesListServlet extends AbstractJsonResponseServlet {

    def bundleContext

    @Override
    protected void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse
        response) throws ServletException, IOException {
        writeJsonResponse(response, adaptersMap + servicesMap)
    }

    @Activate
    void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext
    }

    private def getAdaptersMap() {
        def adapters = [:] as TreeMap

        def serviceReferences = bundleContext.getServiceReferences(AdapterFactory, null).findAll { serviceReference ->
            serviceReference.getProperty(AdapterFactory.ADAPTABLE_CLASSES).contains(ResourceResolver.class.name)
        }

        serviceReferences.each { serviceReference ->
            serviceReference.getProperty(AdapterFactory.ADAPTER_CLASSES).each { String adapterClassName ->
                adapters[adapterClassName] = getAdapterDeclaration(adapterClassName)
            }
        }

        adapters
    }

    private def getServicesMap() {
        def services = [:] as TreeMap
        def allServices = [:]

        bundleContext.getAllServiceReferences(null, null).each { serviceReference ->
            def name = serviceReference.getProperty(ComponentConstants.COMPONENT_NAME)
            def objectClass = serviceReference.getProperty(Constants.OBJECTCLASS)

            objectClass.each { className ->
                def implementationClassNames = allServices[className] ?: []

                if (name) {
                    implementationClassNames.add(name)
                }

                allServices[className] = implementationClassNames
            }
        }

        allServices.each { String className, implementationClassNames ->
            services[className] = getServiceDeclaration(className, null)

            if (implementationClassNames.size() > 1) {
                implementationClassNames.each { String implementationClassName ->
                    services[implementationClassName] = getServiceDeclaration(className, implementationClassName)
                }
            }
        }

        services
    }

    private static def getAdapterDeclaration(String className) {
        def simpleName = className.tokenize('.').last()
        def variableName = StringUtils.uncapitalize(simpleName)

        "def $variableName = resourceResolver.adaptTo($className)"
    }

    private static def getServiceDeclaration(String className, implementationClassName) {
        def simpleName = className.tokenize('.').last()
        def variableName = StringUtils.uncapitalize(simpleName)
        def declaration

        if (implementationClassName) {
            def filter = "(${ComponentConstants.COMPONENT_NAME}=$implementationClassName)"

            declaration = "def $variableName = getServices(\"$className\", \"$filter\")[0]"
        } else {
            declaration = "def $variableName = getService(\"$className\")"
        }

        declaration
    }
}
