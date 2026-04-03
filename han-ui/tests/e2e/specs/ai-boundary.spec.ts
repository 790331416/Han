import { test, expect } from '../fixtures/test'
import { e2eRuntime } from '../fixtures/test'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  fetchKnowledgeDocuments,
  hitTestKnowledgeBase,
  uploadKnowledgeDocumentBinary
} from '../utils/ai-admin'

function buildUniqueName(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`
}

test('ai knowledge should keep pdf and docx as upload-only until auto parsing is implemented', async ({ request, authSession }) => {
  const kbName = buildUniqueName('文档解析边界')
  const createdKb = await createKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, {
    kbName,
    kbType: 'general',
    description: '中文文件名的 pdf/docx 自动解析边界回归'
  })

  const pdfName = '产品说明.pdf'
  const docxName = '接口说明.docx'
  const expectedError = '当前版本暂仅支持 txt、md、html 自动解析'

  try {
    await uploadKnowledgeDocumentBinary(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId, {
      fileName: pdfName,
      mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.4\n% Han Cloud boundary probe\n', 'utf-8')
    })

    await uploadKnowledgeDocumentBinary(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId, {
      fileName: docxName,
      mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      buffer: Buffer.from('PK\u0003\u0004Han Cloud docx boundary probe', 'utf-8')
    })

    await expect.poll(async () => {
      const docs = await fetchKnowledgeDocuments(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId)
      const pdf = docs.find((item) => item.docName === pdfName)
      const docx = docs.find((item) => item.docName === docxName)
      if (!pdf || !docx) {
        return 'pending'
      }
      return `${pdf.indexStatus}|${docx.indexStatus}`
    }, { timeout: 30000 }).toBe('failed|failed')

    const docs = await fetchKnowledgeDocuments(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId)
    const pdf = docs.find((item) => item.docName === pdfName)
    const docx = docs.find((item) => item.docName === docxName)

    expect(pdf).toBeTruthy()
    expect(docx).toBeTruthy()
    expect(pdf?.indexError).toBe(expectedError)
    expect(docx?.indexError).toBe(expectedError)
    expect(pdf?.paragraphCount ?? 0).toBe(0)
    expect(docx?.paragraphCount ?? 0).toBe(0)

    const hits = await hitTestKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId, '产品说明')
    expect(hits).toEqual([])
  } finally {
    await deleteKnowledgeBase(request, e2eRuntime.apiBaseUrl, authSession.accessToken, createdKb.kbId).catch(() => undefined)
  }
})
