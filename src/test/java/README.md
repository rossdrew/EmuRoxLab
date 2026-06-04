# Testing in Java

## Tools
- JUnit: A unit testing framework for Java, widely used for writing and running tests.
- Mockito: A mocking framework for Java used to create and configure mock objects in tests.
- Jqwick: A property testing framework for Java
- Pitest: A mutation testing framework for Java that helps evaluate the effectiveness of tests.

## Testing Strategies

### Unit Testing

Testing methods and classes as standalone units and asserting outcomes based on given inputs.

```java
    @BeforeEach
    public void setup(){
        //some setup
    }

    @Test
    public void initialTick(){
        //Given
        //When
        //Then
        assertEquals(4, add(2, 2)); //Validate that 2+2=4
    }
```

### Mocking

Creating mock dependencies in order to isolate the unit under test and control its behavior.

```java
    @Test
    public void testWithMocking(){
        //Given
        MyDependency dependency = Mockito.mock(MyDependency.class);
        Mockito.when(dependency.someMethod()).thenReturn("mocked value"); //Mocked response

        MyClass myClass = new MyClass(dependency);

        //When
        String result = myClass.methodUnderTest();

        //Then
        assertEquals("mocked value", result);
        verify(dependency, times(1)).someMethod(); //Validate calls to mock happened
    }
```

### Property Testing

Unit testing where you want to make sure that a property holds for a wide range of inputs, rather than just specific cases.

```java
    @Provide //Values between 0-255
    Arbitrary<Integer> byteValues() {
        return integers().between(0, 0xFF);
    }

    @Property
    public void invalidLocationWraps(@ForAll("byteValues") int value){
        //Use random selection of `value` which is between 0 and 255
    }
```

### Data-Driven/Parameterized Testing

```java
    @ParameterizedTest(name = "ADD: {0}+{1}={2}")
    @CsvSource({
            "2, 2, 4",
            "10, 1,  11",
            "10, -1, 9"
    })
    void additionEdgeCases(int a, boolean b, boolean result) {
        assertEquals(result, add(a,b));
    }
```

### Mutation Testing

Evaluate the effectiveness of tests by introducing changes (mutations) to the code and checking if tests fail as expected.

This is done at the build level.  Running unit tests under th mutation testing framework while the framework mutates the code to find holes in test logic.