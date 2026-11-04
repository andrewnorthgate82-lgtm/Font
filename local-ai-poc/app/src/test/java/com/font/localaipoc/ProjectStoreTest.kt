package com.font.localaipoc

import com.font.studio.*
import org.junit.Test
import org.junit.Rule
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.json.JSONObject
import java.io.File

class ProjectStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun sample(id: String = "p1", workspace: String = "personal"): Project {
        val proposal = Proposal("a","بررسی خبر","آموزشی",listOf(Slide(listOf(
            Layer.Text("title","خبر را بررسی کنیم","headline"),
            Layer.Text("body","منبع را بررسی کن؛ می‌توانیم مکث کنیم.","body"),
            Layer.Image("photo","asset-1",false),
            Layer.Chart("chart",listOf("الف","ب"),listOf(1.0,2.0),"s1")
        ))))
        return Project(id,workspace,Brief("خبر","نوجوانان",ContentKind.MEDIA_LITERACY),listOf(Source("s1","منبع","local:source")),listOf(Revision(1,proposal,"کپشن",listOf(Claim("ادعا",setOf("s1"))))))
    }
    @Test fun fullProjectRoundTripPreservesPersianLayersSourcesClaims() {
        assertEquals(sample(),ProjectCodec.decode(ProjectCodec.encode(sample())))
    }
    @Test fun twoProjectsDoNotOverwriteEachOther() {
        val store=ProjectStore(temp.root)
        store.save(sample("first")); store.save(sample("second"))
        assertEquals(2,store.list().projects.size)
        assertEquals("first",store.load("first").id)
    }
    @Test fun revisionsAndRecoverySurviveReopen() {
        val store=ProjectStore(temp.root)
        val p=sample()
        val changed=p.edit(p.current.proposal,"ویرایش",p.current.claims)
        store.save(changed)
        val reopened=ProjectStore(temp.root).load(p.id)
        val old=reopened.revisions.first()
        store.save(reopened.edit(old.proposal,old.caption,old.claims))
        assertEquals(3,store.load(p.id).revisions.size)
        assertEquals("کپشن",store.load(p.id).current.caption)
        assertEquals("ویرایش",store.load(p.id).revisions[1].caption)
    }
    @Test fun corruptProjectDoesNotHideHealthyProjectOrGetDeleted() {
        val store=ProjectStore(temp.root);store.save(sample())
        val bad=File(temp.root,"personal/bad.json").apply { writeText("broken") }
        assertEquals(1,store.list().unreadableCount)
        assertEquals(1,store.list().projects.size)
        assertTrue(bad.exists())
    }
    @Test fun workspaceSeparationAndPathTraversal() {
        val store=ProjectStore(temp.root,"team")
        assertThrows(IllegalArgumentException::class.java) { store.save(sample()) }
        assertThrows(IllegalArgumentException::class.java) { store.load("../other") }
        assertThrows(IllegalArgumentException::class.java) { ProjectStore(temp.root,"../other") }
        store.save(sample("p1","team"))
        assertTrue(ProjectStore(temp.root).list().projects.isEmpty())
    }
    @Test fun newerSchemaIsRejected() {
        val data=JSONObject(ProjectCodec.encode(sample())).put("schema",99)
        assertThrows(IllegalArgumentException::class.java) { ProjectCodec.decode(data.toString()) }
    }
    @Test fun approvalIsNotRestoredFromDisk() {
        val approved=sample().approve("editor")
        assertNull(ProjectCodec.decode(ProjectCodec.encode(approved)).approval)
    }
    @Test fun legacyMigrationDoesNotOverwriteEditedProject() {
        val legacy=File(temp.root,"studio-project-v1.json").apply { writeText("""{"schema":1,"id":"old","topic":"موضوع","audience":"مخاطب","kind":"POSTER","revisions":[{"title":"عنوان","rationale":"دلیل","body":"متن","caption":"کپشن"}]}""") }
        val store=ProjectStore(File(temp.root,"new"))
        store.migrateLegacy(legacy)
        val p=store.load("old")
        store.save(p.edit(p.current.proposal,"تازه",emptyList()))
        store.migrateLegacy(legacy)
        assertEquals("تازه",store.load("old").current.caption)
        assertEquals(2,store.load("old").current.number)
        assertTrue(legacy.exists())
    }
    @Test fun identityMismatchIsIsolated() {
        val store=ProjectStore(temp.root);store.save(sample())
        File(temp.root,"personal/p1.json").writeText(ProjectCodec.encode(sample("wrong")))
        assertEquals(1,store.list().unreadableCount)
    }
}
