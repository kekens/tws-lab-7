package ru.kekens;

import org.apache.commons.lang.StringUtils;
import org.apache.juddi.api_v3.AccessPointType;
import org.apache.juddi.v3.client.UDDIConstants;
import org.apache.juddi.v3.client.config.UDDIClient;
import org.apache.juddi.v3.client.transport.Transport;
import org.uddi.api_v3.*;
import org.uddi.v3_service.UDDIInquiryPortType;
import org.uddi.v3_service.UDDIPublicationPortType;
import org.uddi.v3_service.UDDISecurityPortType;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

public class Main {

    private static UDDISecurityPortType security = null;
    private static UDDIInquiryPortType inquiry = null;
    private static UDDIPublicationPortType publish = null;

    /**
     * Конструктор для инициализации UDDI-клиента
     */
    public Main() {
        try {
            UDDIClient client = new UDDIClient("META-INF/uddi.xml");
            Transport transport = client.getTransport("default");

            security = transport.getUDDISecurityService();
            inquiry = transport.getUDDIInquiryService();
            publish = transport.getUDDIPublishService();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws RemoteException {
        System.setProperty("org.apache.xmlbeans.javax.xml.accessExternalDTD", "all");
        Main main = new Main();

        // Получаем токен
        String token = getAuthToken("uddiadmin", "12345678");

        // Запускам цикл
        Scanner scanner = new Scanner(System.in);
        String input = null;
        do {
            System.out.println("Выберите команду (R - Зарегистрировать сервис, F - найти сервис, Q - Выйти): ");
            input = scanner.nextLine();

            switch (input) {
                case "R":
                    // Регистрация сервиса
                    registerService(scanner, token);
                    break;
                case "F":
                    // Поиск сервиса и обращение
                    System.out.println("Введите имя искомого сервиса (по умолчанию - AccountService):");
                    String serviceName = scanner.nextLine();
                    if (StringUtils.isEmpty(serviceName.trim())) {
                        serviceName = "AccountService";
                    }

                    // Ищем сервис и проверяем точку доступа
                    String accessPoint = null;
                    try {
                        accessPoint = searchService(scanner, token, serviceName);
                        if (StringUtils.isEmpty(accessPoint)) {
                            System.out.println("Не удалось найти сервис по имени " + serviceName);
                        } else {
                            // Если нашли, вызываем клиента
                            System.out.println("------------");
                            System.out.println("Вызов клиента");
                            System.out.println("------------");

                            WebServiceClient webServiceClient = new WebServiceClient();
                            webServiceClient.main(accessPoint);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    break;
                case "Q":
                    // Выход
                    break;
                default:
                    System.out.println("Неправильная команда!");
                    break;
            }
        } while (!Objects.equals(input, "Q"));

        security.discardAuthToken(new DiscardAuthToken(token));

        scanner.close();
    }

    private static void registerService(Scanner scanner, String token) {
        try {
            // Создаем бизнес, который будет содержать наш сервис
            System.out.println("Введите имя создаваемого бизнеса (по умолчанию - AccountBusiness):");
            String businessEntityName = scanner.nextLine();
            if (StringUtils.isEmpty(businessEntityName.trim())) {
                businessEntityName = "AccountBusiness";
            }

            BusinessEntity myBusEntity = new BusinessEntity();
            Name myBusName = new Name();
            myBusName.setValue(businessEntityName);
            myBusEntity.getName().add(myBusName);

            // Сохраняем бизнес
            SaveBusiness sb = new SaveBusiness();
            sb.getBusinessEntity().add(myBusEntity);
            sb.setAuthInfo(token);
            BusinessDetail bd = publish.saveBusiness(sb);
            String myBusKey = bd.getBusinessEntity().get(0).getBusinessKey();
            System.out.println(businessEntityName + " key:  " + myBusKey);

            // Создаем сервис для сохранения в созданном ранее бизнесе
            System.out.println("Введите имя создаваемого сервиса (по умолчанию - AccountService):");
            String businessServiceName = scanner.nextLine();
            if (StringUtils.isEmpty(businessServiceName.trim())) {
                businessServiceName = "AccountService";
            }

            BusinessService myService = new BusinessService();
            myService.setBusinessKey(myBusKey);
            Name myServName = new Name();
            myServName.setValue(businessServiceName);
            myService.getName().add(myServName);

            // Добавляем техническое описание
            BindingTemplate myBindingTemplate = new BindingTemplate();
            AccessPoint accessPoint = new AccessPoint();
            accessPoint.setUseType(AccessPointType.WSDL_DEPLOYMENT.toString());
            accessPoint.setValue("http://localhost:8081/AccountService?wsdl");
            myBindingTemplate.setAccessPoint(accessPoint);
            BindingTemplates myBindingTemplates = new BindingTemplates();
            myBindingTemplate = UDDIClient.addSOAPtModels(myBindingTemplate);
            myBindingTemplates.getBindingTemplate().add(myBindingTemplate);

            myService.setBindingTemplates(myBindingTemplates);

            // Сохраняем сервис
            SaveService ss = new SaveService();
            ss.getBusinessService().add(myService);
            ss.setAuthInfo(token);
            ServiceDetail sd = publish.saveService(ss);
            String myServKey = sd.getBusinessService().get(0).getServiceKey();
            System.out.println(businessServiceName + " key:  " + myServKey);

            System.out.println("Успешно зарегистрирован сервис!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Метод для поиска сервиса
     * @param scanner     сканнер
     * @param token       токен
     * @param serviceName имя сервиса для поиска
     * @return точка доступа
     *
     * @throws Exception  исключение
     */
    private static String searchService(Scanner scanner, String token, String serviceName) throws Exception {
        BusinessList findBusiness = GetBusinessList(token);
        BusinessInfos businessInfos = findBusiness.getBusinessInfos();
        for (int i = 0; i < businessInfos.getBusinessInfo().size(); i++) {
            GetServiceDetail gsd = new GetServiceDetail();
            for (int k = 0; k < businessInfos.getBusinessInfo().get(i).getServiceInfos().getServiceInfo().size(); k++) {
                gsd.getServiceKey().add(businessInfos.getBusinessInfo().get(i).getServiceInfos().getServiceInfo().get(k).getServiceKey());
            }
            gsd.setAuthInfo(token);
            ServiceDetail serviceDetail = inquiry.getServiceDetail(gsd);
            for (int k = 0; k < serviceDetail.getBusinessService().size(); k++) {
                BusinessService get = serviceDetail.getBusinessService().get(k);

                // Сравниваем все сервисы, и если нашли, возвращаем результат
                if (ListToString(get.getName()).equals(serviceName)) {
                    String accessPoint = getServiceAccessPoint(get.getBindingTemplates());
                    if (StringUtils.isNotEmpty(accessPoint)) {
                        System.out.println("Сервис найден. Точка доступа - " + accessPoint);
                        return accessPoint;
                    }
                }
            }
        }

        return null;
    }

    private static BusinessList GetBusinessList(String token) throws Exception {
        FindBusiness fb = new FindBusiness();
        fb.setAuthInfo(token);
        org.uddi.api_v3.FindQualifiers fq = new org.uddi.api_v3.FindQualifiers();
        fq.getFindQualifier().add(UDDIConstants.APPROXIMATE_MATCH);

        fb.setFindQualifiers(fq);
        Name searchname = new Name();
        searchname.setValue(UDDIConstants.WILDCARD);
        fb.getName().add(searchname);
        BusinessList findBusiness = inquiry.findBusiness(fb);
        return findBusiness;
    }

    private static String getServiceAccessPoint(BindingTemplates bindingTemplates) {
        if (bindingTemplates == null) {
            return null;
        }
        String serviceAccessPoint = null;
        for (int i = 0; i < bindingTemplates.getBindingTemplate().size(); i++) {
            if (bindingTemplates.getBindingTemplate().get(i).getAccessPoint() != null) {
                if (bindingTemplates.getBindingTemplate().get(i).getAccessPoint().getUseType() != null) {
                    if (bindingTemplates.getBindingTemplate().get(i).getAccessPoint().getUseType().equalsIgnoreCase(AccessPointType.WSDL_DEPLOYMENT.toString())) {
                        serviceAccessPoint = bindingTemplates.getBindingTemplate().get(i).getAccessPoint().getValue();
                    }
                }
            }
        }

        return serviceAccessPoint;
    }

    private static String ListToString(List<Name> name) {
        StringBuilder sb = new StringBuilder();
        for (Name value : name) {
            sb.append(value.getValue());
        }
        return sb.toString();
    }

    /**
     * Метод для получения токена аутентификации
     * @param username пользователь
     * @param password пароль
     * @return         токен аутентификации
     */
    private static String getAuthToken(String username, String password) {
        try {
            GetAuthToken getAuthTokenMyPub = new GetAuthToken();
            getAuthTokenMyPub.setUserID(username);
            getAuthTokenMyPub.setCred(password);
            AuthToken myPubAuthToken = security.getAuthToken(getAuthTokenMyPub);
            return myPubAuthToken.getAuthInfo();
        } catch (Exception e) {
            System.out.println("Ошибка при получении токена аутентификации: " + e.getMessage());
        }

        return null;
    }


}