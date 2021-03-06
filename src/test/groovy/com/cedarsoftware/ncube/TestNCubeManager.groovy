package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.ncube.formatters.NCubeTestReader
import com.cedarsoftware.ncube.formatters.NCubeTestWriter
import com.cedarsoftware.util.DeepEquals
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.io.JsonWriter
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.nio.file.Files
import java.nio.file.Paths

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
 * NCubeManager Tests
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the 'License')
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an 'AS IS' BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
public class TestNCubeManager
{
    public static final String APP_ID = 'ncube.test'
    public static final String USER_ID = 'jdirt'
    public static ApplicationID defaultSnapshotApp = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '1.0.0', ReleaseStatus.SNAPSHOT.name())
    public static ApplicationID defaultReleaseApp = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '1.0.0', ReleaseStatus.RELEASE.name())

    @Before
    public void setUp() throws Exception
    {
        TestingDatabaseHelper.setupDatabase()
    }

    @After
    public void tearDown() throws Exception
    {
        TestingDatabaseHelper.tearDownDatabase()
    }

    private static NCubeTest[] createTests()
    {
        CellInfo foo = new CellInfo('int', '5', false, false)
        CellInfo bar = new CellInfo('string', 'none', false, false)
        StringValuePair[] pairs = [new StringValuePair('foo', foo), new StringValuePair('bar', bar)] as StringValuePair[]
        CellInfo[] cellInfos = [foo, bar] as CellInfo[]

        return [new NCubeTest('foo', pairs, cellInfos)] as NCubeTest[]
    }

    private static NCube createCube() throws Exception
    {
        NCube<Double> ncube = NCubeBuilder.getTestNCube2D(true)

        def coord = [gender:'male', age:47]
        ncube.setCell(1.0, coord)

        coord.gender = 'female'
        ncube.setCell(1.1d, coord)

        coord.age = 16
        ncube.setCell(1.5d, coord)

        coord.gender = 'male'
        ncube.setCell(1.8d, coord)

        NCubeManager.createCube(defaultSnapshotApp, ncube, USER_ID)
        NCubeManager.updateTestData(defaultSnapshotApp, ncube.name, new NCubeTestWriter().format(createTests()))
        NCubeManager.updateNotes(defaultSnapshotApp, ncube.name, 'notes follow')
        return ncube
    }

    @Test
    public void testLoadCubes() throws Exception
    {
        NCube<Double> ncube = NCubeBuilder.getTestNCube2D(true)

        def coord = [gender:'male', age:47]
        ncube.setCell(1.0d, coord)

        coord.gender = 'female'
        ncube.setCell(1.1d, coord)

        coord.age = 16
        ncube.setCell(1.5d, coord)

        coord.gender = 'male'
        ncube.setCell(1.8d, coord)

        String version = '0.1.0'
        String name1 = ncube.name

        ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, version, ReleaseStatus.SNAPSHOT.name())
        NCubeManager.createCube(appId, ncube, USER_ID)
        NCubeManager.updateTestData(appId, ncube.name, JsonWriter.objectToJson(coord))
        NCubeManager.updateNotes(appId, ncube.name, 'notes follow')

        assertTrue(NCubeManager.doesCubeExist(appId, name1))

        ncube = NCubeBuilder.testNCube3D_Boolean
        String name2 = ncube.name
        NCubeManager.createCube(appId, ncube, USER_ID)

        NCubeManager.clearCache(appId)
        NCubeManager.getCubeRecordsFromDatabase(appId, '')

        NCube ncube1 = NCubeManager.getCube(appId, name1)
        NCube ncube2 = NCubeManager.getCube(appId, name2)
        assertNotNull(ncube1)
        assertNotNull(ncube2)
        assertEquals(name1, ncube1.name)
        assertEquals(name2, ncube2.name)
        assertTrue(NCubeManager.isCubeCached(appId, name1))
        assertTrue(NCubeManager.isCubeCached(appId, name2))
        NCubeManager.clearCache(appId)
        assertFalse(NCubeManager.isCubeCached(appId, name1))
        assertFalse(NCubeManager.isCubeCached(appId, name2))

        NCubeManager.deleteCube(appId, name1, true, USER_ID)
        NCubeManager.deleteCube(appId, name2, true, USER_ID)

        // Cubes are deleted ('allowDelete' was set to true above)
        assertFalse(NCubeManager.doesCubeExist(appId, name1))
        assertFalse(NCubeManager.doesCubeExist(appId, name2))

        Object[] cubeInfo = NCubeManager.getCubeRecordsFromDatabase(appId, name1)
        assertEquals(0, cubeInfo.length)
        cubeInfo = NCubeManager.getCubeRecordsFromDatabase(appId, name2)
        assertEquals(0, cubeInfo.length)
    }

    @Test
    public void testUpdateSavesTestData() throws Exception
    {
        NCube cube = createCube()
        assertNotNull(cube)

        Object[] expectedTests = createTests()

        // reading from cache.
        String data = NCubeManager.getTestData(defaultSnapshotApp, 'test.Age-Gender')
        assertTrue(DeepEquals.deepEquals(expectedTests, new NCubeTestReader().convert(data).toArray(new NCubeTest[0])))

        // reload from db
        NCubeManager.clearCache()
        data = NCubeManager.getTestData(defaultSnapshotApp, 'test.Age-Gender')
        assertTrue(DeepEquals.deepEquals(expectedTests, new NCubeTestReader().convert(data).toArray(new NCubeTest[0])))

        //  update cube
        NCubeManager.updateCube(defaultSnapshotApp, cube, USER_ID)
        data = NCubeManager.getTestData(defaultSnapshotApp, 'test.Age-Gender')
        assertTrue(DeepEquals.deepEquals(expectedTests, new NCubeTestReader().convert(data).toArray(new NCubeTest[0])))

        assertTrue(NCubeManager.deleteCube(defaultSnapshotApp, cube.name, true, USER_ID))
    }

    @Test
    public void testDoesCubeExistInvalidId()
    {
        try
        {
            NCubeManager.doesCubeExist(defaultSnapshotApp, null)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertEquals('n-cube name cannot be null or empty', e.message)
        }
    }

    @Test
    public void testDoesCubeExistInvalidName()
    {
        try
        {
            NCubeManager.doesCubeExist(null, 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('cannot'))
            assertTrue(e.message.contains('null'))
        }
    }

    @Test
    public void testGetReferencedCubesThatLoadsTwoCubes() throws Exception
    {
        try
        {
            Set<String> set = new HashSet<>()
            NCubeManager.getReferencedCubeNames(defaultSnapshotApp, 'AnyCube', set)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertNull(e.cause)
        }
    }

    @Test
    public void testRenameWithMatchingNames() throws Exception
    {
        try
        {
            NCubeManager.renameCube(defaultSnapshotApp, 'foo', 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertNull(e.cause)
        }
    }

    @Test
    public void testBadCommandCellCommandWithJdbc() throws Exception
    {
        NCube<Object> continentCounty = new NCube<>('test.ContinentCountries')
        continentCounty.applicationID = defaultSnapshotApp
        NCubeManager.addCube(defaultSnapshotApp, continentCounty)
        continentCounty.addAxis(NCubeBuilder.continentAxis)
        Axis countries = new Axis('Country', AxisType.DISCRETE, AxisValueType.STRING, true)
        countries.addColumn('Canada')
        countries.addColumn('USA')
        countries.addColumn('Mexico')
        continentCounty.addAxis(countries)

        NCube<Object> canada = new NCube<>('test.Provinces')
        canada.applicationID = defaultSnapshotApp
        NCubeManager.addCube(defaultSnapshotApp, canada)
        canada.addAxis(NCubeBuilder.provincesAxis)

        NCube<Object> usa = new NCube<>('test.States')
        usa.applicationID = defaultSnapshotApp
        NCubeManager.addCube(defaultSnapshotApp, usa)
        usa.addAxis(NCubeBuilder.statesAxis)

        Map coord1 = new HashMap()
        coord1.put('Continent', 'North America')
        coord1.put('Country', 'USA')
        coord1.put('State', 'OH')

        Map coord2 = new HashMap()
        coord2.put('Continent', 'North America')
        coord2.put('Country', 'Canada')
        coord2.put('Province', 'Quebec')

        continentCounty.setCell(new GroovyExpression('@test.States([:])', null), coord1)
        continentCounty.setCell(new GroovyExpression('\$test.Provinces(crunch)', null), coord2)

        usa.setCell(1.0, coord1)
        canada.setCell(0.78, coord2)

        assertEquals((Double) continentCounty.getCell(coord1), 1.0d, 0.00001d)

        try
        {
            assertEquals((Double) continentCounty.getCell(coord2), 0.78d, 0.00001d)
            fail 'should throw exception'
        }
        catch (RuntimeException e)
        {
            assert e.message.toLowerCase().contains('error occurred executing')
        }

        NCubeManager.createCube(defaultSnapshotApp, continentCounty, USER_ID)
        NCubeManager.createCube(defaultSnapshotApp, usa, USER_ID)
        NCubeManager.createCube(defaultSnapshotApp, canada, USER_ID)

        assertEquals(4, NCubeManager.getCubeNames(defaultSnapshotApp).size())

        // make sure items aren't in cache for next load from db for next getCubeNames call
        // during create they got added to database.
        NCubeManager.clearCache()

        assertEquals(4, NCubeManager.getCubeNames(defaultSnapshotApp).size())

        NCubeManager.clearCache()

        NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, '')
        NCube test = NCubeManager.getCube(defaultSnapshotApp, 'test.ContinentCountries')
        assertEquals((Double) test.getCell(coord1), 1.0d, 0.00001d)

        NCubeManager.deleteCube(defaultSnapshotApp, 'test.ContinentCountries', false, USER_ID)
        NCubeManager.deleteCube(defaultSnapshotApp, 'test.States', false, USER_ID)
        NCubeManager.deleteCube(defaultSnapshotApp, 'test.Provinces', false, USER_ID)
        assertEquals(1, NCubeManager.getCubeNames(defaultSnapshotApp).size())
    }

    @Test
    public void testGetReferencedCubeNames() throws Exception
    {
        NCube n1 = NCubeManager.getNCubeFromResource('template1.json')
        NCube n2 = NCubeManager.getNCubeFromResource('template2.json')

        NCubeManager.createCube(defaultSnapshotApp, n1, USER_ID)
        NCubeManager.createCube(defaultSnapshotApp, n2, USER_ID)

        Set refs = new TreeSet()
        NCubeManager.getReferencedCubeNames(defaultSnapshotApp, n1.name, refs)

        assertEquals(2, refs.size())
        assertTrue(refs.contains('Template2Cube'))

        refs.clear()
        NCubeManager.getReferencedCubeNames(defaultSnapshotApp, n2.name, refs)
        assertEquals(2, refs.size())
        assertTrue(refs.contains('Template1Cube'))

        assertTrue(NCubeManager.deleteCube(defaultSnapshotApp, n1.name, true, USER_ID))
        assertTrue(NCubeManager.deleteCube(defaultSnapshotApp, n2.name, true, USER_ID))

        try
        {
            NCubeManager.getReferencedCubeNames(defaultSnapshotApp, n2.name, null)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('not get referenced')
            assert e.message.toLowerCase().contains('null passed in for set')
        }
    }

    @Test
    public void testGetReferencedCubeNamesSimple() throws Exception
    {
        NCube n1 = NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'aa.json')
        NCube n2 = NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'bb.json')

        Set refs = new TreeSet()
        NCubeManager.getReferencedCubeNames(defaultSnapshotApp, n1.name, refs)

        assertEquals(1, refs.size())
        assertTrue(refs.contains('bb'))

        refs.clear()
        NCubeManager.getReferencedCubeNames(defaultSnapshotApp, n2.name, refs)
        assertEquals(0, refs.size())
    }

    @Test
    public void testReferencedCubeCoordinateNotFound() throws Exception
    {
        NCube n1 = NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'aa.json')
        NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'bb.json')

        try
        {
            Map input = new HashMap()
            input.put('state', 'OH')
            n1.getCell(input)
            fail()
        }
        catch (CoordinateNotFoundException e)
        {
            assertTrue(e.message.contains('oordinate not found'))
        }
    }

    @Test
    public void testDuplicateNCube() throws Exception
    {
        NCube n1 = NCubeManager.getNCubeFromResource('stringIds.json')
        NCubeManager.createCube(defaultSnapshotApp, n1, USER_ID)
        ApplicationID newId = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '1.1.2', ReleaseStatus.SNAPSHOT.name())

        NCubeManager.duplicate(defaultSnapshotApp, newId, n1.name, n1.name, USER_ID)
        NCube n2 = NCubeManager.getCube(defaultSnapshotApp, n1.name)

        assertTrue(NCubeManager.deleteCube(defaultSnapshotApp, n1.name, true, USER_ID))
        assertTrue(NCubeManager.deleteCube(newId, n2.name, true, USER_ID))
        assertTrue(n1.equals(n2))
    }

    @Test
    public void testGetAppNames() throws Exception
    {
        NCube n1 = NCubeManager.getNCubeFromResource('stringIds.json')
        NCubeManager.createCube(defaultSnapshotApp, n1, USER_ID)

        Object[] names = NCubeManager.getAppNames(defaultSnapshotApp.tenant)
        boolean foundName = false
        for (Object name : names)
        {
            if ('ncube.test'.equals(name))
            {
                foundName = true
                break
            }
        }

        Object[] vers = NCubeManager.getAppVersions(defaultSnapshotApp)
        boolean foundVer = false
        String version = '1.0.0'
        for (Object ver : vers)
        {
            if (version.equals(ver))
            {
                foundVer = true
                break
            }
        }

        assertTrue(NCubeManager.deleteCube(defaultSnapshotApp, n1.name, true, USER_ID))
        assertTrue(foundName)
        assertTrue(foundVer)
    }


    @Test
    public void testChangeVersionValue() throws Exception
    {
        NCube n1 = NCubeManager.getNCubeFromResource('stringIds.json')
        ApplicationID newId = defaultSnapshotApp.createNewSnapshotId('1.1.20')

        assertNull(NCubeManager.getCube(defaultSnapshotApp, 'idTest'))
        assertNull(NCubeManager.getCube(newId, 'idTest'))
        NCubeManager.createCube(defaultSnapshotApp, n1, USER_ID)

        assertNotNull(NCubeManager.getCube(defaultSnapshotApp, 'idTest'))
        assertNull(NCubeManager.getCube(newId, 'idTest'))
        NCubeManager.changeVersionValue(defaultSnapshotApp, '1.1.20')

        assertNotNull(NCubeManager.getCube(newId, 'idTest'))

        NCube n2 = NCubeManager.getCube(newId, 'idTest')
        assertEquals(n1, n2)

        assertTrue(NCubeManager.deleteCube(newId, n1.name, true, USER_ID))
    }

    @Test
    public void testUpdateOnDeletedCube() throws Exception
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean

        NCubeManager.createCube(defaultSnapshotApp, ncube1, USER_ID)

        assertTrue(ncube1.numDimensions == 3)

        NCubeManager.deleteCube(defaultSnapshotApp, ncube1.name, USER_ID)

        try
        {
            NCubeManager.updateCube(defaultSnapshotApp, ncube1, USER_ID)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Error updating'))
            assertTrue(e.message.contains('attempting to update deleted cube'))
        }
    }

    @Test
    public void testUpdateTestDataOnDeletedCube() throws Exception
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean

        NCubeManager.createCube(defaultSnapshotApp, ncube1, USER_ID)

        assertTrue(ncube1.numDimensions == 3)

        NCubeManager.deleteCube(defaultSnapshotApp, ncube1.name, USER_ID)

        try
        {
            NCubeManager.updateTestData(defaultSnapshotApp, ncube1.name, USER_ID)
            fail 'should not make it here'
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Cannot update'))
            assertTrue(e.message.contains('deleted'))
        }
    }

    @Test
    public void testConstruction()
    {
        assertNotNull(new NCubeManager())
    }

    @Test
    public void testUpdateNotesOnDeletedCube() throws Exception
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCubeManager.createCube(defaultSnapshotApp, ncube1, USER_ID)
        assertTrue(ncube1.numDimensions == 3)
        NCubeManager.deleteCube(defaultSnapshotApp, ncube1.name, USER_ID)

        try
        {
            NCubeManager.updateNotes(defaultSnapshotApp, ncube1.name, USER_ID)
            fail 'should not make it here'
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Cannot update'))
            assertTrue(e.message.contains('deleted'))
        }
    }

    @Test
    public void testGetNullPersister()
    {
        NCubeManager.nCubePersister = null

        try
        {
            NCubeManager.persister
            fail()
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.toLowerCase().contains('persister not set'))
        }
    }

    @Test
    public void testGetNCubes() throws Exception
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCube ncube2 = NCubeBuilder.getTestNCube2D(true)

        NCubeManager.createCube(defaultSnapshotApp, ncube1, USER_ID)
        NCubeManager.createCube(defaultSnapshotApp, ncube2, USER_ID)

        Object[] cubeList = NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, 'test.%')

        assertTrue(cubeList != null)
        assertTrue(cubeList.length == 2)

        assertTrue(ncube1.numDimensions == 3)
        assertTrue(ncube2.numDimensions == 2)

        ncube1.deleteAxis('bu')
        NCubeManager.updateCube(defaultSnapshotApp, ncube1, USER_ID)
        NCube cube1 = NCubeManager.getCube(defaultSnapshotApp, 'test.ValidTrailorConfigs')
        assertTrue(cube1.numDimensions == 2)    // used to be 3

        assertEquals(4, NCubeManager.releaseCubes(defaultSnapshotApp))

        // After the line below, there should be 4 test cubes in the database (2 @ version 0.1.1 and 2 @ version 0.2.0)
        NCubeManager.createSnapshotCubes(defaultSnapshotApp, '0.2.0')

        ApplicationID newId = defaultSnapshotApp.createNewSnapshotId('0.2.0')

        String notes1 = NCubeManager.getNotes(defaultSnapshotApp, 'test.ValidTrailorConfigs')
        NCubeManager.getNotes(newId, 'test.ValidTrailorConfigs')

        NCubeManager.updateNotes(defaultSnapshotApp, 'test.ValidTrailorConfigs', null)
        notes1 = NCubeManager.getNotes(defaultSnapshotApp, 'test.ValidTrailorConfigs')
        assertTrue(''.equals(notes1))

        NCubeManager.updateNotes(defaultSnapshotApp, 'test.ValidTrailorConfigs', 'Trailer Config Notes')
        notes1 = NCubeManager.getNotes(defaultSnapshotApp, 'test.ValidTrailorConfigs')
        assertTrue('Trailer Config Notes'.equals(notes1))

        NCubeManager.updateTestData(newId, 'test.ValidTrailorConfigs', null)
        String testData = NCubeManager.getTestData(newId, 'test.ValidTrailorConfigs')
        assertTrue(''.equals(testData))

        NCubeManager.updateTestData(newId, 'test.ValidTrailorConfigs', 'This is JSON data')
        testData = NCubeManager.getTestData(newId, 'test.ValidTrailorConfigs')
        assertTrue('This is JSON data'.equals(testData))

        // Verify that you cannot delete a RELEASE ncube
        try
        {
            NCubeManager.deleteCube(defaultSnapshotApp, ncube1.name, false, USER_ID)
            fail()
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains('not'))
            assertTrue(e.message.contains('delete'))
            assertTrue(e.message.contains('nable'))
            assertTrue(e.message.contains('find'))
        }
        try
        {
            NCubeManager.deleteCube(defaultSnapshotApp, ncube2.name, false, USER_ID)
            fail()
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains('not'))
            assertTrue(e.message.contains('delete'))
            assertTrue(e.message.contains('nable'))
            assertTrue(e.message.contains('find'))
        }

        // Delete ncubes using 'true' to allow the test to delete a released ncube.
        NCubeManager.deleteCube(defaultSnapshotApp, ncube1.name, true, USER_ID)
        NCubeManager.deleteCube(defaultSnapshotApp, ncube2.name, true, USER_ID)

        // Delete new SNAPSHOT cubes
        assertTrue(NCubeManager.deleteCube(newId, ncube1.name, false, USER_ID))
        assertTrue(NCubeManager.deleteCube(newId, ncube2.name, false, USER_ID))

        // Ensure that all test ncubes are deleted
        cubeList = NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, 'test.%')
        assertTrue(cubeList.length == 0)
    }

    @Test
    public void testRenameNCube() throws Exception
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCube ncube2 = NCubeBuilder.getTestNCube2D(true)

        try
        {
            NCubeManager.renameCube(defaultSnapshotApp, ncube1.name, 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('not rename'))
            assertTrue(e.message.contains('does not exist'))
        }

        NCubeManager.createCube(defaultSnapshotApp, ncube1, USER_ID)
        NCubeManager.createCube(defaultSnapshotApp, ncube2, USER_ID)

        NCubeManager.renameCube(defaultSnapshotApp, ncube1.name, 'test.Floppy')

        Object[] cubeList = NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, 'test.%')

        assertTrue(cubeList.length == 2)

        NCubeInfoDto nc1 = (NCubeInfoDto) cubeList[0]
        NCubeInfoDto nc2 = (NCubeInfoDto) cubeList[1]

        assertTrue(nc1.toString().startsWith('NONE/ncube.test/1.0.0/SNAPSHOT/test.Age-Gender'))
        assertTrue(nc2.toString().startsWith('NONE/ncube.test/1.0.0/SNAPSHOT/test.Floppy'))

        assertTrue(nc1.name.equals('test.Floppy') || nc2.name.equals('test.Floppy'))
        assertFalse(nc1.name.equals('test.Floppy') && nc2.name.equals('test.Floppy'))

        assertTrue(NCubeManager.deleteCube(defaultSnapshotApp, 'test.Floppy', true, USER_ID))
        assertTrue(NCubeManager.deleteCube(defaultSnapshotApp, ncube2.name, true, USER_ID))

        assertFalse(NCubeManager.deleteCube(defaultSnapshotApp, 'test.Floppy', true, USER_ID))
    }

    @Test
    public void testNCubeManagerGetCubes() throws Exception
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCube ncube2 = NCubeBuilder.getTestNCube2D(true)

        NCubeManager.createCube(defaultSnapshotApp, ncube1, USER_ID)
        NCubeManager.createCube(defaultSnapshotApp, ncube2, USER_ID)

        // This proves that null is turned into '%' (no exception thrown)
        Object[] cubeList = NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, null)

        assertEquals(3, cubeList.length)

        assertTrue(NCubeManager.deleteCube(defaultSnapshotApp, ncube1.name, true, USER_ID))
        assertTrue(NCubeManager.deleteCube(defaultSnapshotApp, ncube2.name, true, USER_ID))
    }

    @Test
    public void testUpdateCubeWithSysClassPath() throws Exception
    {
        String name = 'Fire'
        //  from setup, assert initial classloader condition (www.cedarsoftware.com)
        ApplicationID customId = new ApplicationID('NONE', 'updateCubeSys', '1.0.0', ReleaseStatus.SNAPSHOT.name())
        assertNotNull(NCubeManager.getUrlClassLoader(customId, [:]))
        assertEquals(0, NCubeManager.getCacheForApp(customId).size())

        NCube testCube = NCubeManager.getNCubeFromResource(customId, 'sys.classpath.tests.json')

        assertEquals(1, NCubeManager.getUrlClassLoader(customId, [:]).URLs.length)
        assertEquals(1, NCubeManager.getCacheForApp(customId).size())

        testCube = NCubeManager.getNCubeFromResource(customId, 'sys.classpath.tests.json')   // reload to clear classLoader inside the cell
        NCubeManager.createCube(customId, testCube, USER_ID)

        Map<String, Object> cache = NCubeManager.getCacheForApp(customId)
        assertEquals(1, cache.size())
        assertEquals(testCube, cache.get('sys.classpath'))

        assertTrue(NCubeManager.updateCube(customId, testCube, USER_ID))
        assertNotNull(NCubeManager.getUrlClassLoader(customId, [:]))
        assertEquals(1, NCubeManager.getCacheForApp(customId).size())

        testCube = NCubeManager.getCube(customId, 'sys.classpath')
        cache = NCubeManager.getCacheForApp(customId)
        assertEquals(1, cache.size())
        assertEquals(1, NCubeManager.getUrlClassLoader(customId, [:]).URLs.length)

        //  validate item got added to cache.
        assertEquals(testCube, cache.get('sys.classpath'))
    }

    @Test
    public void testRenameCubeWithSysClassPath() throws Exception
    {
        String name = 'Dude'
        //  from setup, assert initial classloader condition (www.cedarsoftware.com)
        ApplicationID customId = new ApplicationID('NONE', 'renameCubeSys', '1.0.0', ReleaseStatus.SNAPSHOT.name())
        final URLClassLoader urlClassLoader1 = NCubeManager.getUrlClassLoader(customId, [:])
        assertNotNull(urlClassLoader1)
        assertEquals(0, NCubeManager.getCacheForApp(customId).size())

        NCube testCube = NCubeManager.getNCubeFromResource(customId, 'sys.classpath.tests.json')

        final URLClassLoader urlClassLoader = NCubeManager.getUrlClassLoader(customId, [:])
        assertEquals(1, urlClassLoader.URLs.length)
        assertEquals(1, NCubeManager.getCacheForApp(customId).size())

        NCubeManager.clearCache()
        testCube = NCubeManager.getNCubeFromResource(customId, 'sys.classpath.tests.json')        // reload so that it does not attempt to write classLoader cells (which will blow up)
        testCube.name = 'sys.mistake'
        NCubeManager.createCube(customId, testCube, USER_ID)

        Map<String, Object> cache = NCubeManager.getCacheForApp(customId)
        assertEquals(2, cache.size())     // both sys.mistake and sys.classpath are in the cache

        //  validate item got added to cache.
        assertEquals(testCube, cache.get('sys.mistake'))

        assertTrue(NCubeManager.renameCube(customId, 'sys.mistake', 'sys.classpath'))
        assertNotNull(NCubeManager.getUrlClassLoader(customId, [:]))
        assertEquals(1, NCubeManager.getCacheForApp(customId).size())

        testCube = NCubeManager.getCube(customId, 'sys.classpath')
        assertEquals(1, NCubeManager.getCacheForApp(customId).size())
        assertEquals(1, NCubeManager.getUrlClassLoader(customId, [:]).URLs.length)

        //  validate item got added to cache.
        assertEquals(testCube, cache.get('sys.classpath'))
    }

    @Test
    public void testJsonToJavaBackup() throws Exception
    {
        //can remove when this support is gone
        URL u = NCubeManager.class.getResource('/files/oldFormatSimpleJsonArrayTest.json')
        byte[] encoded = Files.readAllBytes(Paths.get(u.toURI()))
        String cubeString = StringUtilities.createString(encoded, 'UTF-8')

        NCube ncube = NCubeManager.ncubeFromJson(cubeString)

        def coord = [Code:'ints']
        Object[] ints = (Object[]) ncube.getCell(coord)
        assertEquals(ints[0], 0L)
        assertEquals(ints[1], 1)
        assertEquals(ints[2], 4L)

        coord.Code = 'strings'
        Object[] strings = (Object[]) ncube.getCell(coord)
        assertEquals(strings[0], 'alpha')
        assertEquals(strings[1], 'bravo')
        assertEquals(strings[2], 'charlie')

        coord.Code = 'arrays'
        Object[] arrays = (Object[]) ncube.getCell(coord)

        Object[] sub1 = (Object[]) arrays[0]
        assertEquals(sub1[0], 0L)
        assertEquals(sub1[1], 1L)
        assertEquals(sub1[2], 6L)

        Object[] sub2 = (Object[]) arrays[1]
        assertEquals(sub2[0], 'a')
        assertEquals(sub2[1], 'b')
        assertEquals(sub2[2], 'c')

        coord.clear()
        coord.Code = 'crazy'
        arrays = (Object[]) ncube.getCell(coord)

        assertEquals('1.0', arrays[0])
        List sub = (List) arrays[1]
        assertEquals('1.a', sub.get(0))
        sub = (List) arrays[2]
        assertEquals('1.b', sub.get(0))
        assertEquals('2.0', arrays[3])
    }

    @Test
    public void testMissingBootstrapException() throws Exception
    {
        try
        {
            NCubeManager.getApplicationID('foo', 'bar', new HashMap())
            fail()
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.contains('Missing sys.bootstrap cube'))
            assertTrue(e.message.contains('0.0.0 version'))
        }
    }

    @Test
    public void testNCubeManagerUpdateCubeExceptions() throws Exception
    {
        try
        {
            NCubeManager.updateCube(defaultSnapshotApp, null, USER_ID)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('cannot be null'))
        }

        NCube testCube = NCubeBuilder.getTestNCube2D(false)
        try
        {
            ApplicationID id = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'DASHBOARD', ApplicationID.DEFAULT_VERSION, ReleaseStatus.SNAPSHOT.name())
            NCubeManager.updateCube(id, testCube, USER_ID)
            fail()
        }
        catch (Exception e)
        {
            assertTrue(e.message.toLowerCase().contains('error updating'))
            assertTrue(e.message.toLowerCase().contains('non-existing cube'))
        }
    }

    @Test
    public void testNCubeManagerCreateCubes() throws Exception
    {
        ApplicationID id = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'DASHBOARD', ApplicationID.DEFAULT_VERSION, ReleaseStatus.SNAPSHOT.name())
        try
        {
            NCubeManager.createCube(id, null, USER_ID)
            fail('should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('cannot be null'))
        }

        NCube ncube1 = createCube()
        try
        {
            createCube()
            fail('Should not make it here')
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.contains('ube'))
            assertTrue(e.message.contains('already exists'))
        }

        NCubeManager.deleteCube(defaultSnapshotApp, ncube1.name, true, USER_ID)
    }

    @Test
    public void testNCubeManagerCreateSnapshots() throws Exception
    {
        try
        {
            NCubeManager.createSnapshotCubes(defaultSnapshotApp, '1.0.0')
            fail('versions are not allowed to match')
        }
        catch (IllegalArgumentException ignore)
        {
            assertTrue(ignore.message.contains('cannot be the same as the RELEASE version.'))
        }

        try
        {
            createCube()
            NCubeManager.releaseCubes(defaultSnapshotApp)
            NCubeManager.createSnapshotCubes(defaultSnapshotApp, '0.1.1')
            NCubeManager.createSnapshotCubes(defaultSnapshotApp, '0.1.1')
            fail('should not make it here')
        }
        catch (IllegalStateException e)
        {
            assert e.message.toLowerCase().contains("not created")
            assert e.message.toLowerCase().contains("matches an existing version")
        }

        NCubeManager.deleteCube(defaultSnapshotApp, 'test.Age-Gender', true, USER_ID)
    }

    @Test
    public void testNCubeManagerDeleteNotExistingCube() throws Exception
    {
        ApplicationID id = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'DASHBOARD', '0.1.0', ReleaseStatus.SNAPSHOT.name())
        assertFalse(NCubeManager.deleteCube(id, 'DashboardRoles', true, USER_ID))
    }

    @Test
    public void testNotes() throws Exception
    {
        try
        {
            NCubeManager.getNotes(defaultSnapshotApp, 'DashboardRoles')
            fail('should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('not'))
            assertTrue(e.message.contains('fetch'))
            assertTrue(e.message.contains('notes'))
        }

        createCube()
        String notes = NCubeManager.getNotes(defaultSnapshotApp, 'test.Age-Gender')
        assertNotNull(notes)
        assertTrue(notes.length() > 0)

        try
        {
            NCubeManager.updateNotes(defaultSnapshotApp, 'test.funky', null)
            fail('should not make it here')
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains('not'))
            assertTrue(e.message.contains('update'))
            assertTrue(e.message.contains('exist'))
        }

        try
        {
            ApplicationID newId = defaultSnapshotApp.createNewSnapshotId('0.1.1')
            NCubeManager.getNotes(newId, 'test.Age-Gender')
            fail('Should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('not'))
            assertTrue(e.message.contains('fetch'))
            assertTrue(e.message.contains('notes'))
        }

        NCubeManager.deleteCube(defaultSnapshotApp, 'test.Age-Gender', true, USER_ID)
    }

    @Test
    public void testNCubeManagerTestData() throws Exception
    {
        try
        {
            NCubeManager.getTestData(defaultSnapshotApp, 'DashboardRoles')
            fail('should not make it here')
        }
        catch (Exception e)
        {
            assertTrue(e instanceof IllegalArgumentException)
        }

        createCube()
        String testData = NCubeManager.getTestData(defaultSnapshotApp, 'test.Age-Gender')
        assertNotNull(testData)
        assertTrue(testData.length() > 0)

        try
        {
            NCubeManager.updateTestData(defaultSnapshotApp, 'test.funky', null)
            fail('should not make it here')
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains('no'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('exist'))
        }

        ApplicationID newId = defaultSnapshotApp.createNewSnapshotId('0.1.1')
        try
        {
            NCubeManager.getTestData(newId, 'test.Age-Gender')
            fail('Should not make it here')
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains('no'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('exist'))
        }

        assertTrue(NCubeManager.deleteCube(defaultSnapshotApp, 'test.Age-Gender', USER_ID))
    }


    @Test
    public void testEmptyNCubeMetaProps() throws Exception
    {
        NCube ncube = createCube()
        String json = ncube.toFormattedJson()
        ncube = NCube.fromSimpleJson(json)
        assertTrue(ncube.metaProperties.size() == 1)  // sha1

        List<Axis> axes = ncube.axes
        for (Axis axis : axes)
        {
            assertTrue(axis.metaProperties.size() == 0)

            for (Column column : axis.columns)
            {
                assertTrue(column.metaProperties.size() == 0)
            }
        }
        NCubeManager.deleteCube(defaultSnapshotApp, ncube.name, true, USER_ID)
    }

    @Test
    public void testLoadCubesWithNullApplicationID() throws Exception
    {
        try
        {
            // This API is now package friendly and only to be used by tests or NCubeManager implementation work.
            NCubeManager.getCubeRecordsFromDatabase(null, '')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('cannot be null')
        }
    }

    @Test
    public void testEnsureLoadedOnCubeThatDoesNotExist() throws Exception
    {
        try
        {
            // This API is now package friendly and only to be used by tests or NCubeManager implementation work.
            NCubeInfoDto dto = new NCubeInfoDto()
            dto.name = 'does_not_exist'
            dto.app = 'NONE'
            dto.tenant = 'NONE'
            dto.status = 'SNAPSHOT'
            dto.version = '1.0.0'

            NCubeManager.ensureLoaded(dto)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.toLowerCase().contains('unable to load'))
        }
    }

    @Test(expected = RuntimeException.class)
    public void testGetNCubesFromResourceException() throws Exception
    {
        NCubeManager.getNCubesFromResource(null)
    }

    @Test
    public void testRestoreCubeWithEmptyArray() throws Exception
    {
        try
        {
            NCubeManager.restoreCube(defaultSnapshotApp, [] as Object[], USER_ID)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Empty array'))
            assertTrue(e.message.contains('to be restored'))
        }
    }

    @Test
    public void testRestoreCubeWithNullArray() throws Exception
    {
        try
        {
            NCubeManager.restoreCube(defaultSnapshotApp, null, USER_ID)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Empty array'))
            assertTrue(e.message.contains('to be restored'))
        }
    }

    @Test
    public void testRestoreCubeWithNonStringArray() throws Exception
    {
        try
        {
            NCubeManager.restoreCube(defaultSnapshotApp, [Integer.MAX_VALUE] as Object[], USER_ID)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Non string name'))
            assertTrue(e.message.contains('to restore'))
        }
    }

    @Test
    public void testRestoreNonExistingCube() throws Exception
    {
        try
        {
            NCubeManager.restoreCube(defaultSnapshotApp, ['fingers'] as Object[], USER_ID)
            fail('should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('not'))
            assertTrue(e.message.contains('restore'))
            assertTrue(e.message.contains('exist'))
        }
    }

    @Test
    public void testRestoreExistingCube() throws Exception
    {
        NCube cube = createCube()
        try
        {
            NCubeManager.restoreCube(defaultSnapshotApp, [cube.name] as Object[], USER_ID)
            fail('should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('already'))
            assertTrue(e.message.contains('restored'))
        }
        NCubeManager.deleteCube(defaultSnapshotApp, cube.name, USER_ID)
    }

    @Test
    public void testRestoreDeletedCube() throws Exception
    {
        NCube cube = createCube()
        Object[] records = NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, '')
        assertEquals(2, records.length)

        assertEquals(0, NCubeManager.getDeletedCubesFromDatabase(defaultSnapshotApp, '').length)

        NCubeManager.deleteCube(defaultSnapshotApp, cube.name, USER_ID)

        assertEquals(1, NCubeManager.getDeletedCubesFromDatabase(defaultSnapshotApp, '').length)

        records = NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, '')
        assertEquals(1, records.length)
        assertTrue(NCubeManager.doesCubeExist(defaultSnapshotApp, cube.name))

        NCubeManager.restoreCube(defaultSnapshotApp, [cube.name] as Object[], USER_ID)
        records = NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, 'test%')
        assertEquals(1, records.length)

        NCubeManager.deleteCube(defaultSnapshotApp, cube.name, USER_ID)
    }

    @Test
    public void testRestoreCubeWithCubeThatDoesNotExist() throws Exception
    {
        try
        {
            NCubeManager.restoreCube(defaultSnapshotApp, ['foo'] as Object[], USER_ID)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('not restore cube'))
            assertTrue(e.message.contains('does not exist'))
        }
    }

    @Test
    public void testGetRevisionHistory() throws Exception
    {
        try
        {
            NCubeManager.getRevisionHistory(defaultSnapshotApp, 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Cannot fetch'))
            assertTrue(e.message.contains('does not exist'))
        }
    }

    @Test
    public void testDeleteWithRevisions() throws Exception
    {
        NCube cube = createCube()
        assertEquals(2, NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, '').length)
        assertEquals(0, NCubeManager.getDeletedCubesFromDatabase(defaultSnapshotApp, null).length)
        assertEquals(1, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).length)

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        NCubeManager.updateCube(defaultSnapshotApp, cube, USER_ID)
        assertEquals(2, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).length)
        assertEquals(2, NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, '').length)
        assertEquals(0, NCubeManager.getDeletedCubesFromDatabase(defaultSnapshotApp, '').length)

        Axis conAxis = NCubeBuilder.continentAxis
        cube.addAxis(conAxis)

        NCubeManager.updateCube(defaultSnapshotApp, cube, USER_ID)

        assertEquals(3, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).length)
        assertEquals(2, NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, '').length)
        assertEquals(0, NCubeManager.getDeletedCubesFromDatabase(defaultSnapshotApp, '').length)

        NCubeManager.deleteCube(defaultSnapshotApp, cube.name, USER_ID)

        assertEquals(1, NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, '').length)
        assertEquals(1, NCubeManager.getDeletedCubesFromDatabase(defaultSnapshotApp, '').length)
        assertEquals(4, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).length)
        assertTrue(NCubeManager.doesCubeExist(defaultSnapshotApp, cube.name))

        NCubeManager.restoreCube(defaultSnapshotApp, [cube.name] as Object[], USER_ID)

        assertEquals(2, NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, '').length)
        assertEquals(0, NCubeManager.getDeletedCubesFromDatabase(defaultSnapshotApp, '').length)
        assertEquals(5, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).length)

        NCubeManager.deleteCube(defaultSnapshotApp, cube.name, USER_ID)

        assertEquals(1, NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, '').length)
        assertEquals(1, NCubeManager.getDeletedCubesFromDatabase(defaultSnapshotApp, '').length)
        assertEquals(6, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).length)
    }

    @Test
    public void testRevisionHistory() throws Exception
    {
        NCube cube = createCube()
        def history = NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name)
        assertEquals(1, history.length)
        assert history[0].name == 'test.Age-Gender'
        assert history[0] instanceof NCubeInfoDetailsDto
        assert history[0].revision == '0'
        assert history[0].createHid == 'jdirt'
        assert history[0].notes == 'notes follow'

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        NCubeManager.updateCube(defaultSnapshotApp, cube, USER_ID)
        history = NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name)
        assertEquals(2, history.length)
        assert history[1] instanceof NCubeInfoDetailsDto
        assert history[1].name == 'test.Age-Gender'
        assert history[1] instanceof NCubeInfoDetailsDto
        assert history[0].revision == '1'
        assert history[1].revision == '0'
        assert history[1].createHid == 'jdirt'
        assert history[1].notes == 'notes follow'
    }

    @Test
    public void testNCubeInfoDto() throws Exception
    {
        NCube cube = createCube()
        def history = NCubeManager.getCubeRecordsFromDatabase(cube.getApplicationID(), '%')
        assertEquals(2, history.length)     // sys.classpath too
        assertTrue history[0] instanceof NCubeInfoDto
        assertFalse history[0] instanceof NCubeInfoDetailsDto
        assertTrue history[1] instanceof NCubeInfoDto
        assertFalse history[1] instanceof NCubeInfoDetailsDto

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        NCubeManager.updateCube(defaultSnapshotApp, cube, USER_ID)
        history = NCubeManager.getCubeRecordsFromDatabase(cube.getApplicationID(), '%')
        assertEquals(2, history.length)
        assertTrue history[0] instanceof NCubeInfoDto
        assertFalse history[0] instanceof NCubeInfoDetailsDto
        assertTrue history[1] instanceof NCubeInfoDto
        assertFalse history[1] instanceof NCubeInfoDetailsDto
    }

    @Test
    public void testResolveClasspathWithInvalidUrl() throws Exception
    {
        NCubeManager.clearCache()
        NCube cube = NCubeManager.getNCubeFromResource('sys.classpath.invalid.url.json')
        NCubeManager.updateCube(defaultSnapshotApp, cube, USER_ID)
        createCube()

        // force reload from hsql and reget classpath
        assertNotNull(NCubeManager.getUrlClassLoader(defaultSnapshotApp, [:]))

        NCubeManager.clearCache(defaultSnapshotApp)
        assertNotNull(NCubeManager.getUrlClassLoader(defaultSnapshotApp, [:]))

        NCubeManager.getCube(defaultSnapshotApp, 'test.AgeGender')
        GroovyClassLoader loader = (GroovyClassLoader) NCubeManager.getUrlClassLoader(defaultSnapshotApp, [:])
        assertEquals(0, loader.URLs.length)
    }

    @Test
    public void testResolveClassPath()
    {
        loadTestClassPathCubes()

        def map = [env:'DEV']
        NCube baseCube = NCubeManager.getCube(defaultSnapshotApp, 'sys.classpath.base')

        assertEquals('http://www.cedarsoftware.com/tests/ncube/cp1/', baseCube.getCell(map))
        map.env = 'CERT'
        assertEquals('http://www.cedarsoftware.com/tests/ncube/cp2/', baseCube.getCell(map))

        NCube classPathCube = NCubeManager.getCube(defaultSnapshotApp, 'sys.classpath')
        List<String> list = (List<String>) classPathCube.getCell(map)
        assertEquals(1, list.size());
        assertEquals('http://www.cedarsoftware.com/tests/ncube/cp2/', list.get(0));
    }

    @Test
    public void testResolveRelativeUrl()
    {
        // Sets App classpath to http://www.cedarsoftware.com
        NCubeManager.getNCubeFromResource(ApplicationID.defaultAppId, 'sys.classpath.cedar.json')

        // Rule cube that expects tests/ncube/hello.groovy to be relative to http://www.cedarsoftware.com
        NCube hello = NCubeManager.getNCubeFromResource(ApplicationID.defaultAppId, 'resolveRelativeHelloGroovy.json')

        // When run, it will set up the classpath (first cube loaded for App), and then
        // it will run the rule cube.  This cube has a relative URL (relative to the classpath above).
        // The code from the website will be pulled down, executed, and the result (Hello, World.)
        // will be returned.
        String s = (String) hello.getCell([:])
        assertEquals('Hello, world.', s)

        String absUrl = NCubeManager.resolveRelativeUrl(ApplicationID.defaultAppId, 'tests/ncube/hello.groovy')
        assertEquals('http://www.cedarsoftware.com/tests/ncube/hello.groovy', absUrl)
    }

    @Test
    public void testResolveUrlBadArgs()
    {
        try
        {
            NCubeManager.resolveRelativeUrl(ApplicationID.defaultAppId, null)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('resolve'))
            assertTrue(e.message.contains('null'))
            assertTrue(e.message.contains('empty'))
        }
    }

    @Test
    public void testResolveUrlFullyQualified()
    {
        String url = 'http://www.cedarsoftware.com'
        String ret = NCubeManager.resolveRelativeUrl(ApplicationID.defaultAppId, url)
        assertEquals(url, ret)

        url = 'https://www.cedarsoftware.com'
        ret = NCubeManager.resolveRelativeUrl(ApplicationID.defaultAppId, url)
        assertEquals(url, ret)

        url = 'file://Users/joe/Development'
        ret = NCubeManager.resolveRelativeUrl(ApplicationID.defaultAppId, url)
        assertEquals(url, ret)
    }

    @Test
    public void testResolveUrlBadApp()
    {
        Object o = NCubeManager.resolveRelativeUrl(new ApplicationID('foo', 'bar', '1.0.0', ReleaseStatus.SNAPSHOT.name()), 'tests/ncube/hello.groovy')
        assertNull o
    }

    @Test
    public void testGetApplicationId()
    {
        loadTestClassPathCubes()
        loadTestBootstrapCubes()

        ApplicationID bootAppId = NCubeManager.getApplicationID(defaultSnapshotApp.tenant, defaultSnapshotApp.app, null)
        assertEquals(defaultSnapshotApp, bootAppId)

        Map map = new HashMap()
        map.put('env', 'DEV')

        bootAppId = NCubeManager.getApplicationID(defaultSnapshotApp.tenant, defaultSnapshotApp.app, map)
        assertEquals(defaultSnapshotApp.tenant, bootAppId.tenant)
        assertEquals(defaultSnapshotApp.app, bootAppId.app)
        assertEquals(defaultSnapshotApp.version, '1.0.0')
        assertEquals(defaultSnapshotApp.status, bootAppId.status)
    }

    @Test
    public void testEnsureLoadedException()
    {
        try
        {
            NCubeManager.ensureLoaded(null)
            fail()
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.toLowerCase().contains('failed'))
            assertTrue(e.message.toLowerCase().contains('retrieve cube from cache'))
        }
    }

    @Test
    public void testMutateReleaseCube()
    {
        NCube cube = NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'latlon.json')
        NCubeManager.createCube(defaultSnapshotApp, cube, USER_ID)
        Object[] cubeInfos = NCubeManager.getCubeRecordsFromDatabase(defaultSnapshotApp, '%')
        assertNotNull(cubeInfos)
        assertEquals(2, cubeInfos.length)
        NCubeManager.releaseCubes(defaultSnapshotApp)
        try
        {
            NCubeManager.deleteCube(defaultReleaseApp, cube.name, USER_ID)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('cannot'))
            assertTrue(e.message.contains('deleted'))
        }

        try
        {
            NCubeManager.renameCube(defaultReleaseApp, cube.name, 'jumbo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('rename'))
        }

        try
        {
            NCubeManager.restoreCube(defaultReleaseApp, [cube.name] as Object[], USER_ID)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('restore'))
        }

        try
        {
            NCubeManager.updateCube(defaultReleaseApp, cube, USER_ID)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('update'))
        }

        try
        {
            NCubeManager.changeVersionValue(defaultReleaseApp, '1.2.3')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('change'))
            assertTrue(e.message.contains('version'))
        }

        try
        {
            NCubeManager.duplicate(defaultSnapshotApp, defaultReleaseApp, cube.name, 'jumbo', USER_ID)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('duplicate'))
            assertTrue(e.message.contains('version'))
        }
    }

    @Test
    public void testCircularCubeReference()
    {
        NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'a.json')
        NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'b.json')
        NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'c.json')

        Set<String> names = new TreeSet<>()
        NCubeManager.getReferencedCubeNames(defaultSnapshotApp, 'a', names)
        assertEquals(3, names.size())
        assertTrue(names.contains('a'))
        assertTrue(names.contains('b'))
        assertTrue(names.contains('c'))
    }

    private static void loadTestClassPathCubes()
    {
        NCube cube = NCubeManager.getNCubeFromResource(ApplicationID.defaultAppId, 'sys.versions.json')
        NCubeManager.createCube(defaultSnapshotApp, cube, USER_ID)
        cube = NCubeManager.getNCubeFromResource('sys.classpath.local.json')
        NCubeManager.createCube(defaultSnapshotApp, cube, USER_ID)
        cube = NCubeManager.getNCubeFromResource('sys.classpath.json')
        NCubeManager.updateCube(defaultSnapshotApp, cube, USER_ID)
        cube = NCubeManager.getNCubeFromResource('sys.classpath.base.json')
        NCubeManager.createCube(defaultSnapshotApp, cube, USER_ID)
    }

    private static void loadTestBootstrapCubes()
    {
        ApplicationID appId = defaultSnapshotApp.createNewSnapshotId('0.0.0')

        NCube cube = NCubeManager.getNCubeFromResource(appId, 'sys.bootstrap.json')
        NCubeManager.createCube(appId, cube, USER_ID)
        cube = NCubeManager.getNCubeFromResource('sys.version.json')
        NCubeManager.createCube(appId, cube, USER_ID)
        cube = NCubeManager.getNCubeFromResource('sys.status.json')
        NCubeManager.createCube(appId, cube, USER_ID)
    }
}
