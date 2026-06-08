import { useNavigate } from 'react-router-dom';
import { PageShell } from '../../components/layout/PageShell';

interface Row {
  group: string;
  span: number;
  slot: string;
  fixed: string;
  walkin: string;
}

// Static pricing (matches the alobo "Xem sân và bảng giá" screen).
const ROWS: Row[] = [
  { group: 'T2 - T6', span: 3, slot: '5h - 10h', fixed: '80.000 đ', walkin: '100.000 đ' },
  { group: '', span: 0, slot: '10h - 17h', fixed: '60.000 đ', walkin: '80.000 đ' },
  { group: '', span: 0, slot: '17h - 23h', fixed: '150.000 đ', walkin: '170.000 đ' },
  { group: 'T7 - CN', span: 3, slot: '5h - 10h', fixed: '100.000 đ', walkin: '120.000 đ' },
  { group: '', span: 0, slot: '10h - 17h', fixed: '80.000 đ', walkin: '100.000 đ' },
  { group: '', span: 0, slot: '17h - 23h', fixed: '160.000 đ', walkin: '180.000 đ' },
];

export default function PriceTablePage() {
  const navigate = useNavigate();
  return (
    <PageShell title="Xem sân và bảng giá" onBack={() => navigate(-1)} maxWidth="max-w-3xl">
      <h2 className="mb-3 text-lg font-semibold">Bảng giá sân</h2>
      <div className="overflow-hidden rounded-lg border border-white/20 bg-white text-gray-800">
        <div className="bg-white py-2 text-center text-lg font-bold border-b border-gray-200">Pickleball</div>
        <table className="w-full text-center text-sm">
          <thead>
            <tr className="text-gray-700">
              <th className="border-b border-gray-200 px-3 py-3 font-semibold">Thứ</th>
              <th className="border-b border-l border-gray-200 px-3 py-3 font-semibold">Khung giờ</th>
              <th className="border-b border-l border-gray-200 px-3 py-3 font-semibold">Cố định</th>
              <th className="border-b border-l border-gray-200 px-3 py-3 font-semibold">Vãng lai</th>
            </tr>
          </thead>
          <tbody>
            {ROWS.map((r, i) => (
              <tr key={i}>
                {r.span > 0 && (
                  <td rowSpan={r.span} className="border-b border-gray-200 px-3 py-3 font-medium align-middle">
                    {r.group}
                  </td>
                )}
                <td className="border-b border-l border-gray-200 px-3 py-3">{r.slot}</td>
                <td className="border-b border-l border-gray-200 px-3 py-3">{r.fixed}</td>
                <td className="border-b border-l border-gray-200 px-3 py-3">{r.walkin}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </PageShell>
  );
}
