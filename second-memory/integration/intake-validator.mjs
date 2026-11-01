export const code = async (inputs) => {
  const b = inputs.body;
  if (!b || typeof b !== 'object' || Array.isArray(b)) throw new Error('invalid_body');
  const id = b.segment_id;
  const session = b.session_id;
  const text = b.text;
  if (typeof id !== 'string' || !/^[A-Za-z0-9_.-]{1,160}$/.test(id)) throw new Error('invalid_segment_id');
  if (typeof session !== 'string' || !/^[A-Za-z0-9_.-]{1,120}$/.test(session)) throw new Error('invalid_session_id');
  if (typeof text !== 'string' || !text.trim() || text.length > 24000) throw new Error('invalid_text');
  if (typeof b.recorded_at !== 'number' || !Number.isSafeInteger(b.recorded_at) || b.recorded_at < 0 || b.recorded_at > 4102444800000) throw new Error('invalid_recorded_at');
  const {createHash} = await import('node:crypto');
  const sha = createHash('sha256').update(JSON.stringify([id,session,b.recorded_at,text])).digest('hex');
  return {segment_id:id, content_sha256:sha, records:[{
    segment_id:id,session_id:session,recorded_at:new Date(b.recorded_at).toISOString(),
    text,status:'Получено',analysis:'',source:'android-intake-v1',content_sha256:sha
  }]};
};
