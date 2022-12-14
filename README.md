# Spring AOP로 회원의 접근 인가 로직 분리하기(1)



## 이 글에 대해서

- 해당 글은 AOP를 통해 로직을 분리할 때, 메서드의 파라미터를 aspectj의 **리플렉션**으로 활용하는 실습을 진행한 예제입니다.
- 예제 코드는 https://github.com/CMSSKKK/aop-reflection에서 확인할 수 있습니다.

### 토이 프로젝트로 그룹 지도 서비스를 개발 중에 있습니다.

- 회원들이 각각 자신의 지도를 생성 및 장소들을 등록하고 다른 회원들과 지도를 공유하는 서비스입니다.
- 서비스의 도메인에 대해서 간단하게 예시를 들면 각각의 지도는 github repository입니다.
- 그리고 repository의 collaborator들이 그룹 멤버라고 할 수 있습니다.
- 그래서 그룹 멤버에 대한 레벨을 HOST, MAINTAIN, READ 권한으로 나누어서 역할마다 접근 권한을 다르게 가집니다.

### AOP 적용 계기

- 위에 도메인 설명처럼 지도와 관련한 모든 로직에서 접근 권한을 체크하는 로직을 필요로 합니다.
- 그룹에 속한 회원이 아니라면 지도를 조회하거나, 장소를 추가, 수정, 삭제를 하지 못하도록 막아야하기 때문입니다.
- 처음에 깊은 고민없이 구현했을 때, 지도, 장소, 카테고리, 댓글과 같은 서비스로직에 그룹 멤버를 조회하고, 접근권한을 체크하는 로직을 repository를 의존해서 개발했습니다.
- 코드의 중복이 계속해서 발생했고, 결합도와 의존성에 대한 문제에 대해서도 고민하게 되었습니다.
- 여러가지 방식의 리팩토링 방법을 찾게 되었고, 여러 방식에 대한 고민 끝에 AOP를 적용하게 되었습니다.
- 해당 해결과정에 대한 고민과 실제 코드는 다음 글에서 이어서 작성하려고 합니다.



## 1. 파라미터의 이름, 타입으로  파라미터의 value를 찾기

- 회원의 아이디 값과 접근하고자하는 데이터의 아이디를 받는 서비스로직이 있습니다.
- `loginMemberId`와 `accessNumber`를 받아서 단순히 누가 어떤 데이터에 접근했다는 메시지를 남기는 반환하는 예제입니다.
- 회원이 데이터에 접근할 수 있는지에 대한 로직을 필요로합니다.
  - 메서드 내부에서 처리를 해도되지만 이러한 로직을 AOP로 분리하고자 합니다.

```java
@Component
public class ParametersAccessService {

    private static String READ_MESSAGE = "%d번 멤버가 %d번 data에 READ 권한이 필요한 접근을 했어요.";
    private static String MAINTAIN_MESSAGE = "%d번 멤버가 %d번 data를 MAINTAIN 권한이 필요한 접근을 했어요.";
    private static String HOST_MESSAGE = "%d번 멤버가 %d번 data를 HOST 권한이 필요한 접근을 했어요.";

    @RequiredPermission(requiredLevel = MemberLevel.READ) // AOP 적용을 위한 어노테이션
    public String readInfos(Long loginMemberId, Long accessNumber) {
        return String.format(READ_MESSAGE, loginMemberId, accessNumber);
    }

    @RequiredPermission(requiredLevel = MemberLevel.MAINTAIN)
    public String maintainInfos(Long loginMemberId, Long accessNumber) {
        return String.format(MAINTAIN_MESSAGE, loginMemberId, accessNumber);
    }

    @RequiredPermission(requiredLevel = MemberLevel.HOST)
    public String hostInfos(Long loginMemberId, Long accessNumber) {
        return String.format(HOST_MESSAGE, loginMemberId, accessNumber);
    }
}

```

- MemberLevel이라는 enum으로 접근권한에 대해서 정의합니다.

```java
public enum MemberLevel {
    HOST, MAINTAIN, READ
}
```

- PermissionLevel이라는 커스텀 어노테이션을 생성합니다.
- 어노테이션 내에 requiredLevel이라는 필드를 통해서 메서드에 필요한 권한이 무엇인지를 편리하게 체크할 수 있습니다.
- 해당 어노테이션을 포인트컷으로 활용합니다.

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredPermission {

    MemberLevel requiredLevel() default MemberLevel.READ;
}
```

- 접근권한에 대한 정보를 담는 객체를 구현합니다.

```java
@Getter
@ToString
@RequiredArgsConstructor
public class MemberAccessInfo {

    private final Long loginMemberId;
    private final Long accessNumber;

}
```

- Aspect 클래스와 메서드를 구현합니다.

```java
@Aspect
@Component
@Slf4j
public class PermissionParametersAspect {

    private static final String LOGIN_ID_PARAM = "loginMemberId"; // 찾을 파라미터의 이름
    private static final String ACCESS_NUMBER_PARAM = "accessNumber"; // 찾을 파라미터의 이름
		
  	// 해당 어노테이션이 붙은 메서드의 실행전에 로직을 실행
    @Before("@annotation(com.example.aopreflection.aspect.RequiredPermission)") 
    public void validateMemberLevel(JoinPoint joinPoint) { // 접근권한 검증 로직

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
				
      	// 메서드에 부착된 어노테이션의 정보를 가져옵니다. 
        RequiredPermission requiredPermission = signature.getMethod().getAnnotation(RequiredPermission.class);
			
        Object[] args = joinPoint.getArgs(); // 메서드의 파라미터의 값 배열을 꺼내옵니다.
        String[] parameterNames = signature.getParameterNames(); // 메서드의 파라미터들의 이름 배열을 꺼내옵니다. 
        Class<?>[] parameterTypes = signature.getParameterTypes(); // 메서드의 파라미터들의 타입 배열을 꺼내옵니다. 
				
      	// 로그를 통한 확인(참고)
        for (int i = 0; i < args.length; i++) {
            log.info("args[{}] = {}", i, args[i].toString());
        }
        log.info("====================================");
        for (int i = 0; i < parameterNames.length; i++) {
            log.info("parametersNames[{}] = {}", i, parameterNames[i]);
        }
        log.info("====================================");
        for (int i = 0; i < parameterTypes.length; i++) {
            log.info("parameterTypes[{}] = {}", i, parameterTypes[i].toString());
        }
				
      	// 파라미터 정보들의 배열을 통해서 MemberAccessInfo를 생성합니다. 
        MemberAccessInfo memberAccessInfo = getMemberAccessInfo(args, parameterNames, parameterTypes);
				
      	// TODO 권한 체크 로직
      	// 로그인 멤버가 특정 데이터에 접근할 권한이 있는지를 확인하는 로직
      	// level에 맞춰서 권한이 없다면 예외를 던지는 로직을 구현합니다... 해당 예제에서는 생략합니다. 
        MemberLevel memberLevel = requiredPermission.requiredLevel();
        log.info("required level = {}", requiredPermission.requiredLevel());
        
      	// ...생략

    }

    private MemberAccessInfo getMemberAccessInfo(Object[] args, String[] parameterNames, Class[] parameterTypes) {
        Object loginId = null; // 필요한 파라미터 정보
        Object accessNumber = null; 
				
      	// 파라미터들의 배열, 즉 3개의 정보들은 같은 인덱스를 가지게 됩니다. 
      	// 파라미터의 이름, 타입을 통해서 객체의 값을 찾습니다. 
        for (int i = 0; i < parameterNames.length; i++) {
          	// loginMemberId를 찾는 로직 
           if (parameterNames[i].equals(LOGIN_ID_PARAM) && parameterTypes[i].equals(Long.class) && loginId == null) {
                loginId = args[i]; 
           }
           // accessNumber를 찾는 로직  
           if (parameterNames[i].equals(ACCESS_NUMBER_PARAM) && parameterTypes[i].equals(Long.class) && accessNumber == null) {
               accessNumber = args[i];
           }
        }
      	// 해당 메서드에 정보가 담겨있지 않을 수 있기 때문에 null을 체크합니다. 
        checkNull(loginId, accessNumber);
				
      	// 타입을 캐스팅해서 객체를 생성, 반환합니다.
        return new MemberAccessInfo((Long) loginId, (Long) accessNumber);
    }

    private void checkNull(Object arg1, Object arg2) {
        if(arg1 == null || arg2 == null) {
            throw new NullPointerException("파라미터가 null 입니다");
        }
    }

}

```



### 테스트 해보기

- 실질적으로 권한을 체크하고 예외를 던지는 로직이 없기 때문에 로그와 반환값을 통해서 정상적으로 리플렉션을 통한 값이 저장되었는지 확인합니다.

```java
@SpringBootTest
class AccessServiceTest {

    @Autowired
    private ParametersAccessService parametersAccessService;
  
    private final Long loginMemberId = 1L;
    private final Long accessNumber = 1L;

    @Test
    void ParametersAspectTest() {
        assertThat(parametersAccessService.readInfos(loginMemberId, accessNumber))
                .isEqualTo("1번 멤버가 1번 data에 READ 권한이 필요한 접근을 했어요.");
        assertThat(parametersAccessService.maintainInfos(loginMemberId, accessNumber))
                .isEqualTo("1번 멤버가 1번 data에 MAINTAIN 권한이 필요한 접근을 했어요.");
        assertThat(parametersAccessService.hostInfos(loginMemberId, accessNumber)).
                isEqualTo("1번 멤버가 1번 data에 HOST 권한이 필요한 접근을 했어요.");
    }
```

- 메서드의 결과값 테스트는 당연히 성공합니다.

```
2022-12-15 02:19:30.231  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : args[0] = 1
2022-12-15 02:19:30.232  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : args[1] = 1
2022-12-15 02:19:30.232  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : ====================================
2022-12-15 02:19:30.232  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parametersNames[0] = loginMemberId
2022-12-15 02:19:30.232  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parametersNames[1] = accessNumber
2022-12-15 02:19:30.233  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : ====================================
2022-12-15 02:19:30.233  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parameterTypes[0] = class java.lang.Long
2022-12-15 02:19:30.233  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parameterTypes[1] = class java.lang.Long
2022-12-15 02:19:30.244  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : MemberAccessInfo(loginMemberId=1, accessNumber=1)
2022-12-15 02:19:30.244  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : required level = READ
2022-12-15 02:19:30.329  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : args[0] = 1
2022-12-15 02:19:30.329  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : args[1] = 1
2022-12-15 02:19:30.329  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : ====================================
2022-12-15 02:19:30.329  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parametersNames[0] = loginMemberId
2022-12-15 02:19:30.329  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parametersNames[1] = accessNumber
2022-12-15 02:19:30.329  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : ====================================
2022-12-15 02:19:30.329  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parameterTypes[0] = class java.lang.Long
2022-12-15 02:19:30.329  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parameterTypes[1] = class java.lang.Long
2022-12-15 02:19:30.329  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : MemberAccessInfo(loginMemberId=1, accessNumber=1)
2022-12-15 02:19:30.330  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : required level = MAINTAIN
2022-12-15 02:19:30.330  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : args[0] = 1
2022-12-15 02:19:30.332  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : args[1] = 1
2022-12-15 02:19:30.332  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : ====================================
2022-12-15 02:19:30.332  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parametersNames[0] = loginMemberId
2022-12-15 02:19:30.332  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parametersNames[1] = accessNumber
2022-12-15 02:19:30.332  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : ====================================
2022-12-15 02:19:30.333  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parameterTypes[0] = class java.lang.Long
2022-12-15 02:19:30.333  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : parameterTypes[1] = class java.lang.Long
2022-12-15 02:19:30.333  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : MemberAccessInfo(loginMemberId=1, accessNumber=1)
2022-12-15 02:19:30.333  INFO 62906 --- [    Test worker] c.e.a.aspect.PermissionParametersAspect  : required level = HOST
```

- AOP가 적용되었고, 로직이 정상적으로 호출되었는지는 로그를 통해서 확인할 수 있습니다.
- 배열들의 값과 어노테이션의 requiredLevel의 필드값을 정상적으로 호출하고, `MemberAccessInfo` 객체가 정상적으로 생성된 것을 확인할 수 있습니다.



### 문제점

- 접근 권한에 대한 파라미터들을 리플렉션을 이용해서 확인하고 객체도 생성했습니다.
- 하지만 이 방법을 활용하게 되면 해당 `@RequiredPermission` 어노테이션이 붙은 메서드의 파라미터의 이름에 수정, 오타가 생긴다면 정상적으로 작동하지 않습니다.
  - 파라미터를 찾지못해서 NullPointerException이 발생합니다.
- 그리고 새로운 기능이 추가되어 메서드가 추가된다면 메서드의 파라미터 이름을 꼭 일치시켜야만하는 강제성이 부여됩니다.
  - 단순히 Long 타입만을 체크한다면 파라미터 순서 혹은 다른 Long 타입의 파라미터가 포함된 메서드에서 정상적인 값을 찾지 못할 수 있기 때문입니다.
- 로직은 분리를 했지만, 핵심 서비스 로직이 AOP에 결합되는 듯한 느낌을 줍니다.



### 파라미터의 이름에 종속되지 않는 방법은 없을까?

- 파라미터의 타입을 새로 생성하는 방법이 있습니다. 즉, 메서드에 커스텀 객체를 넘겨주는 방식입니다.
- 물론 이 로직 또한 어노테이션이 붙어서 AOP가 작동하기위해서 메서드의 파라미터 타입을 강제하는 느낌이 들지만, 새로 코드를 이해하는 개발자와 유지 보수 측면에서 더 이해하기 쉬울 것으로 생각합니다.



## 2. 커스텀 객체를 파라미터로 넘겨서 타입만으로  파라미터 value 찾기

- 이전 사용되었던 MemberAccessInfo를 Service 로직의 파라미터로 넘겨줍니다.
- 로직은 동일합니다.

```java
@Component
public class CustomObjectAccessService {

    private static String READ_MESSAGE = "%d번 멤버가 %d번 data에 READ 권한이 필요한 접근을 했어요.";
    private static String MAINTAIN_MESSAGE = "%d번 멤버가 %d번 data에 MAINTAIN 권한이 필요한 접근을 했어요.";
    private static String HOST_MESSAGE = "%d번 멤버가 %d번 data에 HOST 권한이 필요한 접근을 했어요.";

    @RequiredPermission(requiredLevel = MemberLevel.READ)
    public String readInfos(MemberAccessInfo memberAccessInfo) { // 파라미터를 MemberAccessInfo로 수정
        return String.format(READ_MESSAGE, memberAccessInfo.getLoginMemberId(), memberAccessInfo.getAccessNumber());
    }

    @RequiredPermission(requiredLevel = MemberLevel.MAINTAIN)
    public String maintainInfos(MemberAccessInfo memberAccessInfo) {
        return String.format(MAINTAIN_MESSAGE, memberAccessInfo.getLoginMemberId(), memberAccessInfo.getAccessNumber());
    }

    @RequiredPermission(requiredLevel = MemberLevel.HOST)
    public String hostInfos(MemberAccessInfo memberAccessInfo) {
        return String.format(HOST_MESSAGE, memberAccessInfo.getLoginMemberId(), memberAccessInfo.getAccessNumber());
    }

}
```

- AOP의 로직을 수정합니다.
- Stream을 활용해서 훨씬 간단하게 로직을 처리할 수 있습니다.

```java
@Component
@Aspect
@Slf4j
public class PermissionMemberAccessInfoAspect {

    @Before("@annotation(com.example.aopreflection.aspect.RequiredPermission)")
    public void validateMemberLevel(JoinPoint joinPoint) {

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
				
        RequiredPermission requiredPermission = signature.getMethod().getAnnotation(RequiredPermission.class);
        log.info("required level = {}", requiredPermission.requiredLevel());
      	
      	// 파라미터의 값 배열만으로 필요한 객체를 찾아옵니다. 
        MemberAccessInfo memberAccessInfo = getMemberAccessInfo(joinPoint.getArgs());
        log.info(memberAccessInfo.toString());
    }

    private MemberAccessInfo getMemberAccessInfo(Object[] args) {
        return Arrays.stream(args)
                .filter(a -> a instanceof MemberAccessInfo) // 타입 체크 
                .map(MemberAccessInfo.class::cast) // 캐스팅
                .findFirst() 
                .orElseThrow(AccessException::new); // 찾는 파라미터가 없다면 예외반환
    }

}

```

- 테스트를 통해서 확인해봅니다.

```java
@SpringBootTest
class AccessServiceTest {

    @Autowired
    private CustomObjectAccessService customObjectAccessService;

    private final Long loginMemberId = 1L;
    private final Long accessNumber = 1L;

    @Test
    void MemberAccessInfoAspectTest() {
        assertThat(customObjectAccessService.readInfos(new MemberAccessInfo(loginMemberId, accessNumber)))
                .isEqualTo("1번 멤버가 1번 data에 READ 권한이 필요한 접근을 했어요.");
        assertThat(customObjectAccessService.maintainInfos(new MemberAccessInfo(loginMemberId, accessNumber)))
                .isEqualTo("1번 멤버가 1번 data에 MAINTAIN 권한이 필요한 접근을 했어요.");
        assertThat(customObjectAccessService.hostInfos(new MemberAccessInfo(loginMemberId, accessNumber)))
                .isEqualTo("1번 멤버가 1번 data에 HOST 권한이 필요한 접근을 했어요.");
    }
}

```

```2022-12-15 02:44:54.779  INFO 63274 --- [    Test worker] c.e.a.a.PermissionMemberAccessInfoAspect : required level = READ
2022-12-15 02:44:54.779  INFO 63274 --- [    Test worker] c.e.a.a.PermissionMemberAccessInfoAspect : required level = READ
2022-12-15 02:44:54.791  INFO 63274 --- [    Test worker] c.e.a.a.PermissionMemberAccessInfoAspect : MemberAccessInfo(loginMemberId=1, accessNumber=1)
2022-12-15 02:44:54.873  INFO 63274 --- [    Test worker] c.e.a.a.PermissionMemberAccessInfoAspect : required level = MAINTAIN
2022-12-15 02:44:54.874  INFO 63274 --- [    Test worker] c.e.a.a.PermissionMemberAccessInfoAspect : MemberAccessInfo(loginMemberId=1, accessNumber=1)
2022-12-15 02:44:54.875  INFO 63274 --- [    Test worker] c.e.a.a.PermissionMemberAccessInfoAspect : required level = HOST
2022-12-15 02:44:54.875  INFO 63274 --- [    Test worker] c.e.a.a.PermissionMemberAccessInfoAspect : MemberAccessInfo(loginMemberId=1, accessNumber=1)
```

- 로그를 통해서 정상적으로 AOP 로직 실행 및 파라미터의 값을 찾은 것을 확인할 수 있습니다.



## 3. 결론

- AOP 로직에서 joinpoint로 메서드의 파라미터들을 리플렉션을 통해서 사용할 수 있습니다.

  ```java
  import org.aspectj.lang.reflect.MethodSignature;
  ```

- 위에서 설명한 2가지 예시 모두 동작 방식은 동일합니다.
- 2번째의 커스텀 객체를 통한 활용도 같은 타입의 파라미터가 추가로 필요한 메서드가 생긴다면 파라미터의 이름을 확인해야합니다.

- **AOP의 적용을 통해서 메서드의 파라미터의 타입 또는 파라미터의 이름에 강제성이 생깁니다.**
- 하지만 **커스텀 어노테이션과 AOP 적용을 통해서 로직을 분리시키고 코드의 중복을 최소화할 수 있습니다. **



### References

- https://www.baeldung.com/spring-aop-get-advised-method-info
- https://github.com/jjik-muk/sikdorak/tree/dev/be





