import { useTranslation } from 'react-i18next';
import { Button } from '../ui/Button';

const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined;
const configured =
  !!clientId && clientId !== 'FILL_IN' && !clientId.startsWith('your-google-client-id');

/**
 * Google sign-in button. Disabled until VITE_GOOGLE_CLIENT_ID is set and the
 * backend /api/auth/google endpoint (Day 5) exists.
 */
export function GoogleButton() {
  const { t } = useTranslation();
  return (
    <Button
      variant="outline"
      fullWidth
      disabled={!configured}
      title={configured ? undefined : 'Sắp có — cần cấu hình Google OAuth2'}
      onClick={() => {
        /* Day 5: trigger Google Identity flow → POST /api/auth/google { idToken } */
      }}
    >
      <span aria-hidden className="font-bold text-base">G</span> {t('auth.googleLogin')}
    </Button>
  );
}
